// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline
import social.aloha.core.ui.RichTextColors

/**
 * A small conversation as Nextcloud Social serves it: one post above the focused one, a reply and a
 * reply to that below, reactions only from their own route, and two versions in the edit history.
 */
private class Conversation : Dispatcher() {
    private val template = JsonObject(NumberedTimeline.homeTemplate() + ("reblog" to JsonNull))

    private fun status(id: String, replyTo: String?, vararg extra: Pair<String, JsonPrimitive>) = JsonObject(
        template + ("id" to JsonPrimitive(id)) + ("in_reply_to_id" to (replyTo?.let(::JsonPrimitive) ?: JsonNull)) +
            extra,
    )

    override fun dispatch(request: RecordedRequest): MockResponse = body(request.url.encodedPath)?.let {
        MockResponse.Builder().code(200).body(it).addHeader("content-type", "application/json").build()
    }
        ?: MockResponse.Builder().code(404).body("{}").build()

    private fun body(path: String): String? = when {
        "/statuses/gone" in path -> null

        path.endsWith("/favourite") -> status(
            "f",
            "a1",
            "favourited" to JsonPrimitive(true),
            "favourites_count" to JsonPrimitive(1),
        ).toString()

        path.endsWith(
            "/statuses/f",
        ) -> status("f", "a1", "edited_at" to JsonPrimitive("2026-09-29T11:00:00.000Z")).toString()

        path.endsWith("/context") -> JsonObject(
            mapOf(
                "ancestors" to JsonArray(listOf(status("a1", null))),
                "descendants" to JsonArray(listOf(status("r1", "f"), status("r2", "r1"))),
            ),
        ).toString()

        path.endsWith("/reactions") -> """[{"name":"🎉","count":2,"me":false}]"""

        path.endsWith("/history") -> history().toString()

        else -> null
    }

    private fun history(): JsonArray {
        val fields = setOf("account", "spoiler_text", "sensitive", "created_at")
        val first = JsonObject(template.filterKeys { it in fields + "content" })
        return JsonArray(
            listOf(
                first,
                JsonObject(
                    template.filterKeys {
                        it in fields
                    } + ("content" to JsonPrimitive("<p>Edited</p>")),
                ),
            ),
        )
    }
}

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ThreadViewModelTest {
    private val clock = Clock.systemUTC()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountsDb = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val cache = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val accounts = AccountRepository(
        accountsDb.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        clock,
        scope,
    )
    private val clients =
        ClientFactory(OkHttpClient(), RateLimiter(nowMillis = clock::millis), Dispatchers.IO, accounts)
    private val statuses = StatusRepository(cache.statusDao(), clock)
    private val server = MockWebServer().apply {
        dispatcher = Conversation()
        start()
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        scope.cancel()
        accountsDb.close()
        cache.close()
        server.close()
    }

    private suspend fun open(statusId: String): ThreadViewModel {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val apiBase = server.url("/")
        val capabilities = ServerCapabilities.minimal(apiBase.toString()).copy(softwareName = "nextcloud-social")
        val account = accounts.signedIn(
            NewAccount(apiBase.host, "6", "alice", "Alice", null, null, capabilities, profilePending = false),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
        return ThreadViewModel(
            ThreadKey(account.id, statusId),
            accounts,
            ThreadRepository(statuses, clients),
            StatusInteractions(statuses, clients),
            RichTextCache(),
            clock,
        ).apply { onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray)) }
    }

    private suspend fun ThreadViewModel.await(predicate: (ThreadUiState) -> Boolean): ThreadUiState =
        withTimeout(10.seconds) { uiState.first(predicate) }

    private fun ThreadUiState.posts() = items.filterIsInstance<ThreadItem.Post>()

    @Test
    fun `the conversation lays out above and below the focused post, indented by who answers whom`() = runBlocking {
        val state = open("f").await { !it.loading && it.posts().size == 4 }
        assertEquals(
            listOf("a1" to 0, "f" to 0, "r1" to 1, "r2" to 2),
            state.posts().map {
                it.row.statusId to it.depth
            },
        )
        assertEquals(listOf(false, true, false, false), state.posts().map { it.focused })
        assertTrue(state.edited)
    }

    @Test
    fun `reactions, which the status never carries on Nextcloud Social, come from their own route`() = runBlocking {
        val state = open("f").await { StatusListKind.Reactions in it.lists }
        assertEquals(2, state.lists[StatusListKind.Reactions])
        assertEquals(listOf("🎉"), state.posts().single { it.focused }.row.reactions.map { it.name })
    }

    @Test
    fun `a favourite in the thread shows at once and offers who favourited`() = runBlocking {
        val viewModel = open("f")
        viewModel.await { !it.loading && it.posts().size == 4 }
        viewModel.onToggle("f", Toggle.Favourite)
        val state = viewModel.await {
            it.posts().single { post -> post.focused }.row.state.favourited &&
                StatusListKind.FavouritedBy in it.lists
        }
        assertEquals(1, state.lists[StatusListKind.FavouritedBy])
    }

    @Test
    fun `the edit history reads newest last, as the server sends it`() = runBlocking {
        val viewModel = open("f")
        viewModel.await { !it.loading && it.posts().size == 4 }
        viewModel.onHistory()
        val history = viewModel.await { it.history != null }.history!!
        assertEquals(listOf("Edited"), history.drop(1).map { it.body.text.trim() })
        viewModel.onHistoryDismissed()
        assertEquals(null, viewModel.await { it.history == null }.history)
    }

    @Test
    fun `a post the server no longer has says so`() = runBlocking {
        assertTrue(open("gone").await { it.gone }.gone)
    }
}
