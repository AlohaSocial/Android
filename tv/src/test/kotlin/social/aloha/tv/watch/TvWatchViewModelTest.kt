// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.watch

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.profile.Reports
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.SignedInFixture

/** Bob's video v1, marked sensitive, with chapters in its description and one comment; bob is not followed. */
private class WatchServer : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path ${request.body?.utf8().orEmpty()}"
        return when {
            path.endsWith("/statuses/v1/context") -> json("""{"ancestors":[],"descendants":[$COMMENT]}""")
            path.endsWith("/statuses/v1") -> json(VIDEO)
            path.endsWith("/relationships") -> json("""[{"id":"2","following":false}]""")
            path.endsWith("/accounts/2/follow") -> json("""{"id":"2","following":true}""")
            path.endsWith("/preferences") -> json("{}")
            else -> json("{}")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val BOB = """{"id":"2","username":"bob","acct":"bob","display_name":"Bob"}"""
        const val VIDEO = """{"id":"v1","account":$BOB,"sensitive":true,"spoiler_text":"Flashing lights",""" +
            """"content":"<p>A tour</p><p>0:00 Start<br>1:30 The harbour</p>",""" +
            """"video":{"title":"Harbour tour"},""" +
            """"media_attachments":[{"id":"m1","type":"video","url":"https://cloud.example/m1.mp4"}]}"""
        const val COMMENT = """{"id":"c1","account":$BOB,"content":"<p>Lovely</p>","in_reply_to_id":"v1"}"""
    }
}

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TvWatchViewModelTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = WatchServer()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var watch: TvWatchViewModel

    @Before
    fun open() = runBlocking {
        // the player is touched on the main looper only, as in the app
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        val base = server.url("/")
        fixture.signIn(base, ServerCapabilities.minimal(base.toString()).copy(watchPositions = true))
        // as in the app, where a video opens from a signed-in home: the accounts are read before the page
        withTimeout(15.seconds) { fixture.accounts.activeOnceRead.first { it != null } }
        watch = TvWatchViewModel(
            "v1",
            ApplicationProvider.getApplicationContext(),
            fixture.accounts,
            ThreadRepository(fixture.statuses, fixture.clients),
            fixture.watchPositions(),
            ProfileRepository(fixture.clients, fixture.statuses, FollowedAuthors(fixture.clients)),
            Reports(fixture.clients),
            SensitiveMedia(fixture.clients),
            scope,
        )
    }

    @After
    fun close() {
        watch.player.release()
        scope.cancel()
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    /** Runs the main looper, where the page's work happens, until [done] holds. */
    private suspend fun until(done: () -> Boolean) = withTimeoutOrNull(15.seconds) {
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TICK_MILLIS))
            delay(TICK_MILLIS)
        }
    } ?: throw AssertionError("gave up waiting at ${watch.uiState.value}; asked ${answers.asked}")

    private suspend fun loaded(): WatchUiState {
        until { watch.uiState.value.let { !it.loading && it.following != null } }
        return watch.uiState.value
    }

    @Test
    fun `a video marked sensitive waits behind its warning, and plays once asked for`() = runBlocking {
        val state = loaded()
        assertTrue(state.covered)
        assertEquals("Flashing lights", state.warning)
        assertNull(watch.player.currentMediaItem)

        watch.onReveal()
        until { watch.player.currentMediaItem != null }
        assertEquals("https://cloud.example/m1.mp4", watch.player.currentMediaItem?.localConfiguration?.uri.toString())
    }

    @Test
    fun `the page has the video's chapters, its comments, and follows who posted it`() = runBlocking {
        val state = loaded()
        assertEquals("Harbour tour", state.title)
        assertEquals(listOf("Start", "The harbour"), state.chapters.map { it.title })
        assertEquals(listOf("Lovely"), state.comments.map { it.text })

        watch.onFollow()
        until { watch.uiState.value.following == true }
    }

    @Test
    fun `a report names the video and what is wrong with it`() = runBlocking {
        loaded()
        watch.onReport(ReportReason.Spam)
        until { watch.uiState.value.reported }
        val report = answers.asked.single { it.startsWith("POST /api/v1/reports") }
        assertTrue(report, "category=spam" in report && "status_ids%5B%5D=v1" in report && "account_id=2" in report)
    }

    private companion object {
        const val TICK_MILLIS = 20L
    }
}
