// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.ContentKind
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.WatchKey
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.ui.RichTextColors

/**
 * A federated PeerTube video by bob, undescribed, with chapters in its description and one comment;
 * bob is not followed until asked; every request is remembered.
 */
private class VideoServer : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path"
        return when {
            path.endsWith("/statuses/v1/context") -> json("""{"ancestors":[],"descendants":[$COMMENT]}""")
            path.endsWith("/statuses/v1") -> json(VIDEO)
            path.endsWith("/relationships") -> json("""[{"id":"2","following":false}]""")
            path.endsWith("/accounts/2/follow") -> json("""{"id":"2","following":true}""")
            else -> json("{}")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val BOB = """{"id":"2","username":"bob","acct":"bob@tube.example","display_name":"Bob"}"""
        const val VIDEO = """{"id":"v1","account":$BOB,"local":false,""" +
            """"content":"<p>A tour</p><p>0:00 Start<br>1:30 The harbour</p>",""" +
            """"video":{"title":"Harbour tour","views":12,"likes":3,"dislikes":1},""" +
            """"media_attachments":[{"id":"m1","type":"video","url":"https://cloud.example/media/m1.mp4"}]}"""
        const val COMMENT = """{"id":"c1","account":$BOB,"content":"<p>Lovely</p>","in_reply_to_id":"v1"}"""
    }
}

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchViewModelTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = VideoServer()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private lateinit var reader: SignedInAccount
    private lateinit var watch: WatchViewModel

    @Before
    fun open() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        reader = fixture.signIn(
            server.url("/"),
            ServerCapabilities.minimal(server.url("/").toString()).copy(watchPositions = true),
        )
        watch = WatchViewModel(
            WatchKey(reader.id, "v1"),
            fixture.accounts,
            ThreadRepository(fixture.statuses, fixture.clients),
            fixture.statuses,
            StatusInteractions(fixture.statuses, fixture.clients),
            ProfileRepository(fixture.clients, fixture.statuses),
            fixture.watchPositions(),
            RichTextCache(),
        )
        watch.onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    private suspend fun loaded() = withTimeout(10.seconds) {
        watch.uiState.first {
            it.video != null &&
                it.following != null
        }
    }

    @Test
    fun `the page plays through the server's playlist, with its chapters, its comments and a way to follow`() =
        runBlocking {
            val state = loaded()
            assertEquals(
                listOf(server.url("/media/playlist/m1").toString(), "https://cloud.example/media/m1.mp4"),
                state.sources.map { it.url },
            )
            assertEquals(listOf("Start", "The harbour"), state.chapters.map { it.title })
            assertEquals(90.0, state.chapters[1].start, 0.0)
            assertEquals(listOf("c1"), state.comments.map { it.statusId })
            assertEquals("Harbour tour", state.video?.video?.title)
            watch.onFollow()
            assertTrue(withTimeout(10.seconds) { watch.uiState.first { it.following == true } }.following == true)
        }

    @Test
    fun `where the reader got to is told, and what the player saw settles the clip`() = runBlocking {
        loaded()
        watch.onProgress(positionSeconds = 42.0, durationSeconds = 600.0, forced = true)
        watch.onSeen(MediaDimensions(width = 1920, height = 1080, duration = 600.0))
        withTimeout(10.seconds) {
            while (answers.asked.none { it == "POST /api/v1/statuses/v1/watched" }) delay(20)
        }
        val kind = withTimeout(10.seconds) {
            var settled = fixture.statuses.kinds(reader.id, listOfNotNull(fixture.statuses.get(reader.id, "v1")))["v1"]
            while (settled == ContentKind.Undetermined) {
                delay(20)
                settled = fixture.statuses.kinds(reader.id, listOfNotNull(fixture.statuses.get(reader.id, "v1")))["v1"]
            }
            settled
        }
        assertEquals(ContentKind.Video, kind)
    }
}
