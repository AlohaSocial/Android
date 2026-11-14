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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AddToCollectionKey
import social.aloha.core.navigation.CollectionsKey
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.SignedInFixture

/** Two collections of the reader's; a new one made on request; [refuse] turns every change down. */
private class Collections : Dispatcher() {
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
class CollectionsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = Collections()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private lateinit var reader: SignedInAccount

    @Before
    fun signIn() = runBlocking {
        reader = fixture.signIn(server.url("/"))
    }

    @After
    fun close() {
        main.clear()
        fixture.close()
        server.close()
    }

    @Test
    fun `the reader's own collections are theirs to make and delete, and a refusal is said once`() = runBlocking {
        val collections =
            main.keep(CollectionsViewModel(CollectionsKey(reader.id), fixture.accounts, fixture.collections))
        val loaded = withTimeout(10.seconds) { collections.uiState.first { !it.loading } }
        assertTrue(loaded.own)
        assertEquals(listOf("1", "2"), loaded.collections.map { it.id })
        collections.onCreate("New", "")
        withTimeout(10.seconds) { collections.uiState.first { it.collections.size == 3 } }
        assertEquals("3", collections.uiState.value.collections.first().id)
        collections.onDelete("1")
        withTimeout(10.seconds) { collections.uiState.first { it.collections.none { item -> item.id == "1" } } }
        answers.refuse = true
        collections.onDelete("2")
        withTimeout(10.seconds) { collections.uiState.first { it.changeFailed } }
        assertTrue(collections.uiState.value.collections.any { it.id == "2" })
        collections.onChangeFailedShown()
        assertFalse(collections.uiState.value.changeFailed)
    }

    @Test
    fun `someone else's collections are only to look at`() = runBlocking {
        val collections = main.keep(
            CollectionsViewModel(
                CollectionsKey(reader.id, ownerId = "someone-else"),
                fixture.accounts,
                fixture.collections,
            ),
        )
        assertFalse(withTimeout(10.seconds) { collections.uiState.first { !it.loading } }.own)
    }

    @Test
    fun `a post goes into a collection once, or into one made for it`() = runBlocking {
        val adding = main.keep(
            AddToCollectionViewModel(AddToCollectionKey(reader.id, "p1"), fixture.accounts, fixture.collections),
        )
        val loaded = withTimeout(10.seconds) { adding.uiState.first { !it.loading } }
        adding.onAdd(loaded.collections.first())
        withTimeout(10.seconds) { adding.uiState.first { "1" in it.added } }
        adding.onCreate("New", "")
        val made = withTimeout(10.seconds) { adding.uiState.first { "3" in it.added } }
        assertEquals("3", made.collections.first().id)
    }
}
