// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.explore.Explore
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.SignedInFixture

/**
 * Alice follows bob, who posted a video, a short, a text and a video marked sensitive; trending are another
 * video and a text; nothing is part watched.
 */
private class ShelvesServer : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            path.endsWith("/timelines/home/") || path.endsWith("/timelines/home") -> json("[$V1,$S1,$T1,$V2]")
            path.contains("/timelines/public") -> json("[$V1]")
            path.endsWith("/trends/statuses") -> json("[$V3,$T1]")
            path.endsWith("/accounts/6/following") -> json("[$BOB]")
            path.endsWith("/videos/continue") -> json("[]")
            path.endsWith("/preferences") -> json("{}")
            else -> json("[]")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val BOB = """{"id":"2","username":"bob","acct":"bob","display_name":"Bob"}"""
        const val WIDE = """"meta":{"original":{"width":1280,"height":720,"duration":95}}"""
        const val TALL = """"meta":{"original":{"width":720,"height":1280,"duration":12}}"""

        fun video(id: String, meta: String, extra: String = "") =
            """{"id":"$id","account":$BOB,"content":"<p>Video $id</p>"$extra,""" +
                """"media_attachments":[{"id":"m$id","type":"video","url":"https://cloud.example/$id.mp4",""" +
                """"preview_url":"https://cloud.example/$id.jpg","blurhash":"LEHV6nWB2yk8pyo0adR*.7kCMdnj",$meta}]}"""

        val V1 = video("v1", WIDE)
        val V2 = video("v2", WIDE, ""","sensitive":true""")
        val V3 = video("v3", WIDE)
        val S1 = video("s1", TALL, ""","tags":[{"name":"shorts"}]""")
        const val T1 = """{"id":"t1","account":$BOB,"content":"<p>Just text</p>"}"""
    }
}

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply {
        dispatcher = ShelvesServer()
        start()
    }
    private lateinit var home: HomeViewModel

    @Before
    fun open() = runBlocking {
        val base = server.url("/")
        fixture.signIn(base, ServerCapabilities.minimal(base.toString()).copy(watchPositions = true))
        home = main.keep(
            HomeViewModel(
                fixture.accounts,
                fixture.timelines,
                fixture.watchPositions(),
                Explore(fixture.clients, fixture.statuses),
                ProfileRepository(fixture.clients, fixture.statuses, FollowedAuthors(fixture.clients)),
                SensitiveMedia(fixture.clients),
                fixture.clock,
            ),
        )
    }

    @After
    fun close() {
        main.clear()
        fixture.close()
        server.close()
    }

    private suspend fun shelves(): Map<ShelfKind, Shelf> = withTimeout(15.seconds) {
        home.uiState.first { state ->
            state.shelves.map { it.kind }.containsAll(listOf(ShelfKind.Following, ShelfKind.Trending))
        }
    }.shelves.associateBy { it.kind }

    @Test
    fun `the shelves hold the videos and shorts of those followed, those people, everyone's and trending`() =
        runBlocking {
            val shelves = shelves()
            assertEquals(listOf("v1", "s1", "v2"), shelves.getValue(ShelfKind.HomeVideo).videos.map { it.id })
            assertEquals(listOf("s1"), shelves.getValue(ShelfKind.Shorts).videos.map { it.id })
            assertEquals(listOf("v1"), shelves.getValue(ShelfKind.FederatedVideo).videos.map { it.id })
            assertEquals(listOf("v3"), shelves.getValue(ShelfKind.Trending).videos.map { it.id })
            assertEquals(listOf("Bob"), shelves.getValue(ShelfKind.Following).people.map { it.name })
            // nothing is part watched, so there is no shelf for it
            assertFalse(ShelfKind.ContinueWatching in shelves)
        }

    @Test
    fun `a video marked sensitive shows no still and plays no preview under the default policy`() = runBlocking {
        val sensitive = shelves().getValue(ShelfKind.HomeVideo).videos.single { it.id == "v2" }
        assertTrue(sensitive.covered)
        assertNull(sensitive.thumbnail)
        assertTrue(sensitive.preview.isEmpty())
        val open = shelves().getValue(ShelfKind.HomeVideo).videos.single { it.id == "v1" }
        assertEquals("https://cloud.example/v1.jpg", open.thumbnail)
        assertEquals(listOf("https://cloud.example/v1.mp4"), open.preview.map { it.url })
    }
}
