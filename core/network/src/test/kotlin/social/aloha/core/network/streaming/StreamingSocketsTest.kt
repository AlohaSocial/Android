// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.streaming

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamingSocketsTest {
    private val status = """{"id":"9","created_at":"2026-10-06T08:00:00.000Z","content":"<p>Edited</p>",""" +
        """"visibility":"public","account":{"id":"1","username":"alice","acct":"alice","display_name":"Alice",""" +
        """"url":"https://example.test/@alice","avatar":"","created_at":"2026-01-01T00:00:00.000Z"}}"""

    @Test
    fun `each event of the reader's stream is told apart, and what it does not act on is left`() {
        assertEquals(StreamEvent.Update, streamEvent("""{"stream":["user"],"event":"update","payload":"{}"}"""))
        assertEquals(StreamEvent.Deleted("9"), streamEvent("""{"stream":["user"],"event":"delete","payload":"9"}"""))
        assertEquals(StreamEvent.Notified, streamEvent("""{"stream":["user"],"event":"notification","payload":"{}"}"""))
        assertEquals(StreamEvent.FiltersChanged, streamEvent("""{"stream":["user"],"event":"filters_changed"}"""))
        val edited = streamEvent(
            """{"stream":["user"],"event":"status.update","payload":${quoted(status)}}""",
        ) as StreamEvent.Edited
        assertEquals("9", edited.status.id)
        assertEquals(
            StreamEvent.NotificationsMerged,
            streamEvent("""{"stream":["user"],"event":"notifications_merged","payload":""}"""),
        )
        val conversation = """{"id":"4","unread":true,"accounts":[],"last_status":$status}"""
        val changed = streamEvent(
            """{"stream":["direct"],"event":"conversation","payload":${quoted(conversation)}}""",
        ) as StreamEvent.ConversationChanged
        assertEquals("4", changed.conversation.id)
        assertEquals("9", changed.conversation.lastStatus?.id)
        assertNull(streamEvent("""{"stream":["direct"],"event":"conversation","payload":"{}"}"""))
        assertNull(streamEvent("""{"stream":["user"],"event":"announcement","payload":"{}"}"""))
        assertNull(streamEvent("not a frame"))
        assertNull(streamEvent("""{"stream":["user"],"event":"status.update","payload":"{broken"}"""))
        assertNull(streamEvent("""{"stream":["user"],"event":"delete","payload":{"id":"9"}}"""))
        assertNull(streamEvent("""{"stream":["user"],"event":["delete"],"payload":"9"}"""))
    }

    @Test
    fun `the socket asks for the user stream with the token in its header, not its address, and for direct`() {
        val server = MockWebServer()
        val subscribed = CopyOnWriteArrayList<String>()
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        subscribed += text
                        webSocket.send("""{"stream":["user"],"event":"delete","payload":"7"}""")
                        webSocket.close(1000, null)
                    }
                },
            ).build(),
        )
        server.start()
        val events = CopyOnWriteArrayList<StreamEvent>()
        val ended = CountDownLatch(1)
        var opened = false
        StreamingSockets(OkHttpClient()).open(
            server.url("/").toString(),
            "secret",
            object : StreamListener {
                override fun onOpen() {
                    opened = true
                }

                override fun onEvent(event: StreamEvent) {
                    events += event
                }

                override fun onClosed(failure: Throwable?) = ended.countDown()
            },
        )
        assertTrue(ended.await(5, TimeUnit.SECONDS))
        val request = server.takeRequest()
        assertEquals("/api/v1/streaming?stream=user", request.target)
        assertEquals("Bearer secret", request.headers["Authorization"])
        assertEquals(listOf("""{"type":"subscribe","stream":"direct"}"""), subscribed)
        assertTrue(opened)
        assertEquals(listOf<StreamEvent>(StreamEvent.Deleted("7")), events)
        server.close()
    }

    private fun quoted(json: String) = "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
