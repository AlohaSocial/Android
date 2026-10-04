// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.database.AccountDao
import social.aloha.core.database.AccountEntity
import social.aloha.core.database.ClientRegistrationEntity
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ClientRegistration
import social.aloha.core.model.LogArea
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.Credentials
import timber.log.Timber

/**
 * Every account on this device and which one is active. Rows live in `accounts.db`, secrets in the
 * vault; the two are written together and neither ever holds the other's data.
 */
@Singleton
public class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val vault: TokenVault,
    private val preferences: AppPreferences,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The account list, collected once for the process; the vault is reconciled before the first rows. */
    private val accountList: StateFlow<List<SignedInAccount>?> = dao.observeAll()
        .onStart { reconcileVault() }
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /** Every account in the order the person arranged them. */
    public val accounts: Flow<List<SignedInAccount>> = accountList.filterNotNull()

    /** The active account, falling back to the first one when none is marked active. */
    public val activeAccount: StateFlow<SignedInAccount?> = combine(accounts, preferences.activeAccountId) { all, id ->
        all.firstOrNull { it.id == id } ?: all.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /** The accounts as they are in the database right now, for work that must not act on a stale list. */
    public suspend fun all(): List<SignedInAccount> = dao.all().map { it.toDomain() }

    /** One account as it is in the database right now, or null once it is signed out. */
    public suspend fun byId(id: String): SignedInAccount? = dao.get(id)?.toDomain()

    /** Stores a newly signed-in account (or refreshes one signed in again) and makes it active. */
    public suspend fun signedIn(account: NewAccount, token: AccessToken): SignedInAccount {
        val existing = dao.find(account.host, account.serverAccountId)
        val id = existing?.id ?: UUID.randomUUID().toString()
        vault.put(VaultKey.AccessToken(id), token.value)
        // the new token comes from the server's registration as it is now
        vault.remove(VaultKey.TokenClient(id))
        val entity = AccountEntity(
            id = id,
            instanceHost = account.host,
            apiBase = account.capabilities.apiBase,
            serverAccountId = account.serverAccountId,
            handle = account.handle,
            displayName = account.displayName,
            avatarUrl = account.avatarUrl,
            headerUrl = account.headerUrl,
            capabilitiesJson = json.encodeToString(ServerCapabilities.serializer(), account.capabilities),
            needsReauth = false,
            profilePending = account.profilePending,
            sortIndex = existing?.sortIndex ?: dao.nextSortIndex(),
            addedAt = existing?.addedAt ?: clock.millis(),
            nextcloudConnected = existing?.nextcloudConnected ?: false,
        )
        dao.upsert(entity)
        preferences.setActiveAccountId(id)
        return entity.toDomain()
    }

    public suspend fun activate(id: String) {
        if (dao.get(id) != null) preferences.setActiveAccountId(id)
    }

    /** The server refused the token: keep the account and its cache, ask for a new sign-in. */
    public suspend fun markNeedsReauth(id: String) {
        Timber.tag(LogArea.Auth.name).i("Account %s needs a new sign-in", id)
        dao.setNeedsReauth(id, needsReauth = true)
    }

    /** Stores freshly detected capabilities; their API base becomes the account's, which a re-probe may have moved. */
    public suspend fun updateCapabilities(id: String, capabilities: ServerCapabilities) {
        val row = dao.get(id) ?: return
        dao.upsert(
            row.copy(
                apiBase = capabilities.apiBase,
                capabilitiesJson = json.encodeToString(ServerCapabilities.serializer(), capabilities),
            ),
        )
    }

    /** Removes the account and its secrets; another account becomes active. Revocation is the caller's. */
    public suspend fun remove(id: String) {
        vault.remove(VaultKey.AccessToken(id))
        vault.remove(VaultKey.AppPassword(id))
        vault.remove(VaultKey.TokenClient(id))
        dao.delete(id)
        if (preferences.activeAccountId.first() == id) preferences.setActiveAccountId(dao.all().firstOrNull()?.id)
    }

    public suspend fun token(id: String): AccessToken? = vault.get(VaultKey.AccessToken(id))?.let {
        AccessToken(it, "")
    }

    public suspend fun credentials(id: String): Credentials = Credentials(
        bearerToken = vault.get(VaultKey.AccessToken(id)),
        nextcloudBasic = vault.get(VaultKey.AppPassword(id)),
    )

    /**
     * The app's OAuth client on [host], registered once per server on this device; for [accountId], the one
     * its token was issued to, which revoking it takes.
     */
    public suspend fun registration(host: String, accountId: String? = null): ClientRegistration? =
        accountId?.let { keptClient(vault.get(VaultKey.TokenClient(it))) }
            ?: dao.registration(host)?.let { row ->
                vault.get(VaultKey.ClientSecret(host))?.let { ClientRegistration(row.clientId, it, row.scopes) }
            }

    /**
     * Stores [registration] as [host]'s. One that replaces another (a moderator's, with the admin scopes)
     * leaves each account there the old client its token was issued to, so the token can still be revoked.
     */
    public suspend fun saveRegistration(host: String, registration: ClientRegistration) {
        val old = registration(host)?.takeIf { it.clientId != registration.clientId }
        if (old != null) {
            dao.all()
                .filter {
                    it.instanceHost.equals(host, ignoreCase = true) &&
                        vault.get(VaultKey.TokenClient(it.id)) == null
                }
                .forEach { vault.put(VaultKey.TokenClient(it.id), "${old.clientId}\n${old.clientSecret}") }
        }
        vault.put(VaultKey.ClientSecret(host), registration.clientSecret)
        dao.upsertRegistration(
            ClientRegistrationEntity(host, registration.clientId, registration.scopes, clock.millis()),
        )
    }

    /** Forgets a registration the server no longer knows (a 401 `unknown client_id` on exchange). */
    public suspend fun forgetRegistration(host: String) {
        vault.remove(VaultKey.ClientSecret(host))
        dao.deleteRegistration(host)
    }

    /**
     * A vault that could not be decrypted, or accounts restored from a backup without their tokens,
     * leave every affected account needing a new sign-in; nothing is deleted.
     */
    internal suspend fun reconcileVault() {
        if (vault.wasLost()) {
            dao.markAllNeedReauth()
            return
        }
        dao.all().filter { !it.needsReauth && vault.get(VaultKey.AccessToken(it.id)) == null }
            .forEach { dao.setNeedsReauth(it.id, needsReauth = true) }
    }

    private fun AccountEntity.toDomain(): SignedInAccount = SignedInAccount(
        id = id,
        host = instanceHost,
        apiBase = apiBase,
        serverAccountId = serverAccountId,
        handle = handle,
        displayName = displayName,
        avatarUrl = avatarUrl,
        headerUrl = headerUrl,
        capabilities = capabilitiesJson?.let {
            try {
                json.decodeFromString(ServerCapabilities.serializer(), it)
            } catch (e: SerializationException) {
                Timber.tag(LogArea.App.name).w("Capabilities of account %s unreadable: %s", id, e.javaClass.simpleName)
                null
            }
        } ?: ServerCapabilities.minimal(apiBase),
        needsReauth = needsReauth,
        profilePending = profilePending,
        addedAt = Instant.ofEpochMilli(addedAt),
        nextcloudConnected = nextcloudConnected,
    )
}

/** What sign-in learned about a new account. */
public data class NewAccount(
    val host: String,
    val serverAccountId: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    val headerUrl: String?,
    val capabilities: ServerCapabilities,
    val profilePending: Boolean,
)

/** A client kept as `id` and secret on two lines; its scopes are not needed to revoke with it. */
private fun keptClient(kept: String?): ClientRegistration? =
    kept?.split('\n', limit = 2)?.takeIf { it.size == 2 }?.let { (id, secret) -> ClientRegistration(id, secret, "") }
