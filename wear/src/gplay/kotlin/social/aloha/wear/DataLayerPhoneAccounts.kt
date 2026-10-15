// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import social.aloha.core.model.HandedOverAccount
import social.aloha.core.model.WatchHandover
import social.aloha.wear.auth.PhoneAccounts
import social.aloha.wear.auth.WatchAccounts

/** The phone app's account, asked for and answered in two Data Layer messages that nothing keeps. */
class DataLayerPhoneAccounts @Inject constructor(@ApplicationContext private val context: Context) : PhoneAccounts {
    override suspend fun reachable(): Boolean = phone() != null

    override suspend fun account(): HandedOverAccount? {
        val phone = phone() ?: return null
        val messages = Wearable.getMessageClient(context)
        val answer = CompletableDeferred<ByteArray>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WatchHandover.ACCOUNT && event.sourceNodeId == phone) answer.complete(event.data)
        }
        return try {
            messages.addListener(listener).await()
            messages.sendMessage(phone, WatchHandover.REQUEST, ByteArray(0)).await()
            withTimeoutOrNull(ANSWER_MILLIS) { answer.await() }?.takeIf { it.isNotEmpty() }?.let(::decode)
        } catch (_: ApiException) {
            null
        } finally {
            messages.removeListener(listener)
        }
    }

    private suspend fun phone(): String? = try {
        Wearable.getCapabilityClient(context)
            .getCapability(WatchHandover.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            .nodes.firstOrNull()?.id
    } catch (_: ApiException) {
        null
    }

    private companion object {
        const val ANSWER_MILLIS = 10_000L
    }
}

/**
 * The phone signed an account out: the watch forgets it at once where it holds that very session, by its token's
 * digest, so a notice that arrives late or comes from another phone never ends a newer handover.
 */
@AndroidEntryPoint
class PhoneSignOutService : WearableListenerService() {
    @Inject lateinit var accounts: WatchAccounts

    @Inject
    @WatchScope
    lateinit var scope: CoroutineScope

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchHandover.SIGNED_OUT) return
        val gone = decode(event.data) ?: return
        scope.launch {
            val current = accounts.current()
            if (current?.shared == true && WatchHandover.digest(current.token) == gone.tokenDigest) {
                accounts.forget()
            }
        }
    }
}

private fun decode(body: ByteArray): HandedOverAccount? = try {
    Json.decodeFromString(HandedOverAccount.serializer(), body.decodeToString())
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

@Module
@InstallIn(SingletonComponent::class)
internal interface FlavorModule {
    @Binds
    fun phoneAccounts(accounts: DataLayerPhoneAccounts): PhoneAccounts
}
