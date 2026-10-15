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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import social.aloha.core.data.AccountRemovalListener
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.model.AccessToken
import social.aloha.core.model.HandedOverAccount
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.WatchHandover
import timber.log.Timber

/**
 * Answers the watch app asking for the account in use here. Wear's Data Layer delivers messages only
 * between the same app on both devices, signed with the same key, so no other app can ask.
 */
@AndroidEntryPoint
class WatchHandoverService : WearableListenerService() {
    @Inject lateinit var accounts: AccountRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchHandover.REQUEST) return
        scope.launch {
            val account = accounts.activeAccount.value ?: accounts.all().firstOrNull()
            val token = account?.let { accounts.token(it.id) }
            val body = if (account == null || token == null) {
                ByteArray(0)
            } else {
                Json.encodeToString(HandedOverAccount.serializer(), account.handedOver(token.value)).encodeToByteArray()
            }
            try {
                Wearable.getMessageClient(this@WatchHandoverService)
                    .sendMessage(event.sourceNodeId, WatchHandover.ACCOUNT, body).await()
            } catch (e: ApiException) {
                Timber.tag(LogArea.Auth.name).w("Watch handover not answered: %s", e.statusCode)
            }
        }
    }
}

/** Tells every watch in reach that an account left the phone, so a watch using it forgets it at once. */
class WatchSignOutNotice @Inject constructor(@ApplicationContext private val context: Context) :
    AccountRemovalListener {
    override suspend fun removed(account: SignedInAccount, token: AccessToken?) {
        // a watch holds this account only by a token handed over; without one there is nothing to end
        val digest = token?.let { WatchHandover.digest(it.value) } ?: return
        val gone = HandedOverAccount(account.apiBase, account.handle, tokenDigest = digest)
        val body = Json.encodeToString(HandedOverAccount.serializer(), gone).encodeToByteArray()
        val nodes = try {
            Wearable.getNodeClient(context).connectedNodes.await()
        } catch (e: ApiException) {
            // no watch, or no Play services: the token is revoked on the server next, which ends it there too
            Timber.tag(LogArea.Auth.name).d("No watch told of the sign-out: %s", e.statusCode)
            return
        }
        val messages = Wearable.getMessageClient(context)
        // each watch on its own, and briefly: none keeps the others uninformed or the revocation waiting
        nodes.forEach { node ->
            try {
                withTimeoutOrNull(SEND_MILLIS) { messages.sendMessage(node.id, WatchHandover.SIGNED_OUT, body).await() }
            } catch (e: ApiException) {
                Timber.tag(LogArea.Auth.name).d("A watch not told of the sign-out: %s", e.statusCode)
            }
        }
    }

    private companion object {
        const val SEND_MILLIS = 5_000L
    }
}

private fun SignedInAccount.handedOver(token: String) = HandedOverAccount(apiBase, handle, token)

@Module
@InstallIn(SingletonComponent::class)
internal interface WatchHandoverModule {
    @Binds
    @IntoSet
    fun signOutNotice(notice: WatchSignOutNotice): AccountRemovalListener
}
