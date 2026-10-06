// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.streaming

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.Closeable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import social.aloha.core.model.Status
import social.aloha.core.network.AlohaJson
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.toDomain

/** What the reader's own stream says happened. */
public sealed interface StreamEvent {
    /** A post arrived on Home. */
    public data object Update : StreamEvent

    /** A post was edited, and now reads as [status]. */
    public data class Edited(val status: Status) : StreamEvent

    /** A post was deleted. */
    public data class Deleted(val statusId: String) : StreamEvent

    /** A notification arrived. */
    public data object Notified : StreamEvent

    /** The reader's filters changed on the server. */
    public data object FiltersChanged : StreamEvent
}

/** Hears one socket: that it opened, each event, and once that it ended, with why when it failed. */
public interface StreamListener {
    public fun onOpen()

    public fun onEvent(event: StreamEvent)

    public fun onClosed(failure: Throwable?)
}

/** Opens a server's streaming socket for the reader's own stream. */
public fun interface UserSockets {
    /** Opens [streamingUrl]'s `user` stream with [token]; closing what it returns ends the socket. */
    public fun open(streamingUrl: String, token: String, listener: StreamListener): Closeable
}

/**
 * Mastodon's streaming socket, `/api/v1/streaming?stream=user`, with the token in the `Authorization`
 * header rather than the address, where access logs would keep it. The socket has no read or call
 * timeout, as it is quiet for minutes at a time, and pings to keep it and the network's paths alive.
 */
@Singleton
public class StreamingSockets @Inject constructor(http: OkHttpClient) : UserSockets {
    private val sockets = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun open(streamingUrl: String, token: String, listener: StreamListener): Closeable {
        val request = Request.Builder()
            .url(streamingUrl.trimEnd('/') + "/api/v1/streaming?stream=user")
            .header("Authorization", "Bearer $token")
            .build()
        val socket = sockets.newWebSocket(request, Relay(listener))
        return Closeable { socket.close(NORMAL, null) }
    }

    private class Relay(private val listener: StreamListener) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

        override fun onMessage(webSocket: WebSocket, text: String) {
            streamEvent(text)?.let(listener::onEvent)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed(null)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onClosed(t)
    }

    private companion object {
        const val NORMAL = 1000
        const val PING_SECONDS = 30L
    }
}

/**
 * One frame of the stream as an event: `{"stream":[…],"event":…,"payload":…}`, the payload a JSON string
 * for an edited post and the bare id for a deletion. Null for what the reader's stream does not act on, or
 * a frame it cannot read.
 */
internal fun streamEvent(text: String): StreamEvent? {
    val frame = runCatching { AlohaJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val payload = (frame["payload"] as? JsonPrimitive)?.contentOrNull
    return when ((frame["event"] as? JsonPrimitive)?.contentOrNull) {
        "update" -> StreamEvent.Update

        "status.update" -> payload?.let { edited ->
            runCatching { AlohaJson.decodeFromString<StatusDto>(edited).toDomain() }.getOrNull()
        }?.let(StreamEvent::Edited)

        "delete" -> payload?.let(StreamEvent::Deleted)

        "notification" -> StreamEvent.Notified

        "filters_changed" -> StreamEvent.FiltersChanged

        else -> null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface StreamingModule {
    @Binds
    fun sockets(streaming: StreamingSockets): UserSockets
}
