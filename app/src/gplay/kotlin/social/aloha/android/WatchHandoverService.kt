// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import social.aloha.core.data.AccountRemovalListener
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.TokenRefresh
import social.aloha.core.data.TokenRenewalListener
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.AccessToken
import social.aloha.core.model.HandedOverAccount
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.WatchHandover
import social.aloha.core.network.TokenRenewal
import timber.log.Timber

/**
 * Answers the watch app asking for the account in use here. Wear's Data Layer delivers messages only
 * between the same app on both devices, signed with the same key, so no other app can ask.
 */
@AndroidEntryPoint
class WatchHandoverService : WearableListenerService() {
    @Inject lateinit var answer: WatchAccountAnswer

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchHandover.REQUEST) return
        scope.launch {
            val body = answer.answer(event.data.decodeToString())
                ?.let { Json.encodeToString(HandedOverAccount.serializer(), it).encodeToByteArray() }
                ?: ByteArray(0)
            try {
                Wearable.getMessageClient(this@WatchHandoverService)
                    .sendMessage(event.sourceNodeId, WatchHandover.ACCOUNT, body).await()
            } catch (e: ApiException) {
                Timber.tag(LogArea.Auth.name).w("Watch handover not answered: %s", e.statusCode)
            }
        }
    }
}

/**
 * The account the phone hands to a watch that asked for it. A watch whose token the server refused names
 * it by its [WatchHandover.digest]: the account holding that token here is renewed first and answered
 * with its new token, whichever account is in use. Otherwise, as at the first handover, the account in
 * use answers, or the first one signed in.
 */
class WatchAccountAnswer internal constructor(
    private val accounts: AccountRepository,
    private val settings: AccountSettingsStore,
    private val renew: suspend (account: SignedInAccount, rejected: String) -> TokenRenewal,
) {
    @Inject constructor(accounts: AccountRepository, settings: AccountSettingsStore, refresh: TokenRefresh) :
        this(accounts, settings, refresh::renew)

    /** The account for a watch whose refused token has [rejectedDigest], empty for none; null with none here. */
    suspend fun answer(rejectedDigest: String): HandedOverAccount? {
        val refused = rejectedDigest.takeIf { it.isNotEmpty() }?.let { digest ->
            accounts.all().firstNotNullOfOrNull { held ->
                accounts.token(held.id)?.value?.takeIf { WatchHandover.digest(it) == digest }?.let { held to it }
            }
        }
        refused?.let { (account, token) -> renew(account, token) }
        val account = refused?.first ?: accounts.activeAccount.value ?: accounts.all().firstOrNull() ?: return null
        val token = accounts.token(account.id) ?: return null
        settings.update(account.id) { it.copy(handedToWatch = true) }
        return account.handedOver(token.value)
    }
}

/** Tells every watch in reach that an account left the phone, so a watch using it forgets it at once. */
class WatchSignOutNotice @Inject constructor(@ApplicationContext private val context: Context) :
    AccountRemovalListener {
    override suspend fun removed(account: SignedInAccount, token: AccessToken?) {
        // a watch holds this account only by a token handed over; without one there is nothing to end
        val digest = token?.let { WatchHandover.digest(it.value) } ?: return
        val gone = HandedOverAccount(account.apiBase, account.handle, tokenDigest = digest)
        // no watch, or no Play services: the token is revoked on the server next, which ends it there too
        toEveryWatch(context, WatchHandover.SIGNED_OUT, Json.encodeToString(HandedOverAccount.serializer(), gone))
    }
}

/**
 * Sends the token that replaced a handed-over account's to every watch in reach, so the watch holding that
 * session, found by the old token's digest, keeps working with it. Only the access token goes: the phone
 * alone refreshes. An account never handed over sends nothing. The notice goes out on [scope], given up
 * after [withinMillis], so it never holds up the renewal; a watch that misses it asks the phone on its 401.
 */
class WatchTokenRenewal internal constructor(
    private val settings: AccountSettingsStore,
    private val scope: CoroutineScope,
    private val send: suspend (body: String) -> Unit,
    private val withinMillis: Long = NOTICE_MILLIS,
) : TokenRenewalListener {
    @Inject constructor(
        @ApplicationContext context: Context,
        settings: AccountSettingsStore,
        @ApplicationScope scope: CoroutineScope,
    ) : this(settings, scope, { body -> toEveryWatch(context, WatchHandover.RENEWED, body) })

    override suspend fun renewed(account: SignedInAccount, old: String, new: String) {
        if (!settings.settings(account.id).first().handedToWatch) return
        val renewed =
            HandedOverAccount(account.apiBase, account.handle, token = new, tokenDigest = WatchHandover.digest(old))
        val body = Json.encodeToString(HandedOverAccount.serializer(), renewed)
        scope.launch { withTimeoutOrNull(withinMillis) { send(body) } }
    }
}

/** [body] to every watch connected now, each on its own and briefly, so none keeps the others or the caller waiting. */
private suspend fun toEveryWatch(context: Context, path: String, body: String) {
    val nodes = try {
        Wearable.getNodeClient(context).connectedNodes.await()
    } catch (e: ApiException) {
        Timber.tag(LogArea.Auth.name).d("No watch told of %s: %s", path, e.statusCode)
        return
    }
    val messages = Wearable.getMessageClient(context)
    nodes.forEach { node ->
        try {
            withTimeoutOrNull(SEND_MILLIS) { messages.sendMessage(node.id, path, body.encodeToByteArray()).await() }
        } catch (e: ApiException) {
            Timber.tag(LogArea.Auth.name).d("A watch not told of %s: %s", path, e.statusCode)
        }
    }
}

private const val SEND_MILLIS = 5_000L

private const val NOTICE_MILLIS = 15_000L

private fun SignedInAccount.handedOver(token: String) = HandedOverAccount(apiBase, handle, token)

@Module
@InstallIn(SingletonComponent::class)
internal interface WatchHandoverModule {
    @Binds
    @IntoSet
    fun signOutNotice(notice: WatchSignOutNotice): AccountRemovalListener

    @Binds
    @IntoSet
    fun tokenRenewal(renewal: WatchTokenRenewal): TokenRenewalListener
}
