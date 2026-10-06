// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.Closeable
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.network.streaming.StreamEvent
import social.aloha.core.network.streaming.StreamListener
import social.aloha.core.network.streaming.UserSockets
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
class UserStreamTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var listener: StreamListener? = null

    @Volatile
    private var asked: Pair<String, String>? = null
    private val sockets = UserSockets { url, token, heard ->
        asked = url to token
        listener = heard
        Closeable { heard.onClosed(null) }
    }
    private val stream = UserStream(fixture.accounts, sockets, fixture.statuses, fixture.filters, TimelineSignals())

    @After
    fun close() {
        scope.cancel()
        fixture.close()
    }

    @Test
    fun `edits and deletions on the stream reach the cache, and it closes in the background`() = runBlocking {
        val base = "https://stream.test/"
        val account = fixture.signIn(
            base.toHttpUrl(),
            ServerCapabilities.minimal(base).copy(streamingUrl = "wss://stream.test"),
        )
        fixture.statuses.save(account.id, StatusSamples.post().copy(id = "9", favourited = true))
        fixture.statuses.save(account.id, StatusSamples.post().copy(id = "10"))
        val inFront = MutableStateFlow(true)
        scope.launch { stream.keepOpen(inFront, MutableStateFlow(true)) }
        withTimeout(5.seconds) { while (listener == null) yield() }
        assertEquals("wss://stream.test", asked?.first)
        listener!!.onOpen()
        withTimeout(5.seconds) { stream.openFor.first { it == account.id } }

        listener!!.onEvent(StreamEvent.Edited(StatusSamples.post("<p>Edited</p>").copy(id = "9")))
        listener!!.onEvent(StreamEvent.Edited(StatusSamples.post("<p>Never here</p>").copy(id = "11")))
        listener!!.onEvent(StreamEvent.Deleted("10"))
        withTimeout(5.seconds) {
            while (fixture.statuses.get(account.id, "10") != null) yield()
            while (fixture.statuses.get(account.id, "9")?.content != "<p>Edited</p>") yield()
        }
        // the stream's copy has no reader: the cache keeps what the reader did, and takes no post it never had
        assertEquals(true, fixture.statuses.get(account.id, "9")?.favourited)
        assertNull(fixture.statuses.get(account.id, "11"))

        inFront.value = false
        withTimeout(5.seconds) { stream.openFor.first { it == null } }
        assertNull(stream.openFor.value)
    }

    @Test
    fun `a server without streaming opens nothing`() = runBlocking {
        val base = "https://plain.test/"
        fixture.signIn(base.toHttpUrl(), ServerCapabilities.minimal(base))
        withTimeout(5.seconds) { fixture.accounts.activeAccount.first { it != null } }
        val job = scope.launch { stream.keepOpen(MutableStateFlow(true), MutableStateFlow(true)) }
        delay(QUIET)
        job.cancel()
        assertNull(listener)
    }

    private companion object {
        val QUIET = 1.seconds
    }
}
