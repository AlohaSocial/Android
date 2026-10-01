// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AddToAlbumKey
import social.aloha.core.navigation.AlbumsKey
import social.aloha.core.testing.SignedInFixture

/** Two albums of the reader's; a new one made on request; [refuse] turns every change down. */
private class Albums : Dispatcher() {
    @Volatile var refuse = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (request.method != "GET" && refuse) return MockResponse.Builder().code(422).body("{}").build()
        return when {
            request.method == "POST" && path.endsWith("/collections") -> json("""{"id":"3","title":"New"}""")
            request.method != "GET" -> json("{}")
            else -> json("""[{"id":"1","title":"Beach"},{"id":"2","title":"Snow"}]""")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}

// the ViewModels' scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumsViewModelTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = Albums()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private lateinit var reader: SignedInAccount

    @Before
    fun signIn() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        reader = fixture.signIn(server.url("/"))
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    @Test
    fun `the reader's own albums are theirs to make and delete, and a refusal is said once`() = runBlocking {
        val albums = AlbumsViewModel(AlbumsKey(reader.id), fixture.accounts, fixture.albums)
        val loaded = withTimeout(10.seconds) { albums.uiState.first { !it.loading } }
        assertTrue(loaded.own)
        assertEquals(listOf("1", "2"), loaded.albums.map { it.id })
        albums.onCreate("New", "")
        withTimeout(10.seconds) { albums.uiState.first { it.albums.size == 3 } }
        assertEquals("3", albums.uiState.value.albums.first().id)
        albums.onDelete("1")
        withTimeout(10.seconds) { albums.uiState.first { it.albums.none { album -> album.id == "1" } } }
        answers.refuse = true
        albums.onDelete("2")
        withTimeout(10.seconds) { albums.uiState.first { it.changeFailed } }
        assertTrue(albums.uiState.value.albums.any { it.id == "2" })
        albums.onChangeFailedShown()
        assertFalse(albums.uiState.value.changeFailed)
    }

    @Test
    fun `someone else's albums are only to look at`() = runBlocking {
        val albums = AlbumsViewModel(AlbumsKey(reader.id, ownerId = "someone-else"), fixture.accounts, fixture.albums)
        assertFalse(withTimeout(10.seconds) { albums.uiState.first { !it.loading } }.own)
    }

    @Test
    fun `a post goes into an album once, or into one made for it`() = runBlocking {
        val adding = AddToAlbumViewModel(AddToAlbumKey(reader.id, "p1"), fixture.accounts, fixture.albums)
        val loaded = withTimeout(10.seconds) { adding.uiState.first { !it.loading } }
        adding.onAdd(loaded.albums.first())
        withTimeout(10.seconds) { adding.uiState.first { "1" in it.added } }
        adding.onCreate("New", "")
        val made = withTimeout(10.seconds) { adding.uiState.first { "3" in it.added } }
        assertEquals("3", made.albums.first().id)
    }
}
