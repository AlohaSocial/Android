// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey

/**
 * The account the watch is signed in to, with the token it holds and the client that token went to, so
 * signing out can revoke it. A [shared] account came from the phone with the phone's own token: signing
 * the watch out only forgets it, since revoking it would sign the phone out too.
 */
@Serializable
data class WatchAccount(
    val apiBase: String,
    val handle: String,
    val token: String,
    val clientId: String = "",
    val clientSecret: String = "",
    val revocationEndpoint: String = "",
    val shared: Boolean = false,
) {
    override fun toString(): String = "WatchAccount($handle at $apiBase)"
}

/** The watch's one account, kept in the Keystore-encrypted vault as everything secret is. */
@Singleton
class WatchAccounts @Inject constructor(private val vault: TokenVault) {
    private val json = Json { ignoreUnknownKeys = true }
    private val changes = MutableStateFlow<WatchAccount?>(null)
    private val changing = Mutex()

    /** The account as it changes, starting from what the vault holds; null while signed out. */
    val account: Flow<WatchAccount?> = flow {
        // under the lock, so a sign-in or sign-out at the same moment is never overwritten by what was read
        changing.withLock { changes.value = current() }
        emitAll(changes)
    }

    // a vault that cannot be read leaves the watch signed out, never a tile or a request that fails
    suspend fun current(): WatchAccount? = try {
        vault.get(VaultKey.WatchAccount)?.let { json.decodeFromString(WatchAccount.serializer(), it) }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    suspend fun keep(account: WatchAccount): Unit = changing.withLock {
        vault.put(VaultKey.WatchAccount, json.encodeToString(WatchAccount.serializer(), account))
        changes.value = account
    }

    suspend fun forget(): Unit = changing.withLock { remove() }

    /** Forgets the account only while it still holds [token]: one signed in since then stays. */
    suspend fun forget(token: String): Unit = changing.withLock {
        if (current()?.token == token) remove()
    }

    private suspend fun remove() {
        vault.remove(VaultKey.WatchAccount)
        changes.value = null
    }
}
