// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.thread.ReplyNudges
import social.aloha.core.data.thread.ThreadOrder
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AccountSettingsStore
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
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline
import social.aloha.core.ui.RichTextColors

/**
 * A small conversation as Nextcloud Social serves it: one post above the focused one, a reply and a
 * reply to that below, reactions on the focused post (which a server before 0.26.221 leaves empty) and from
 * their own route, and two versions in the edit history.
 */
private class Conversation : Dispatcher() {
    /** A reply someone else posted after the thread was first opened. */
    @Volatile var grown = false

    /** The paths asked for, in order. */
    val asked = CopyOnWriteArrayList<String>()

    /** Whether the focused post carries its reactions, as Social does from 0.26.221 on; before, it is empty. */
    @Volatile var reactionsOnStatus = false

    /** Whether the focused post leaves the reactions out altogether. */
    @Volatile var reactionsLeftOut = false

    /** How many more times asking for the custom emoji fails. */
    val emojiFailures = AtomicInteger()

    /** The reader's thread order as the server reports it, `thread_sort`; none sent when null. */
    @Volatile var threadSort: String? = null

    private val template = JsonObject(NumberedTimeline.homeTemplate() + ("reblog" to JsonNull))

    private fun status(id: String, replyTo: String?, vararg extra: Pair<String, JsonPrimitive>) = JsonObject(
        template + ("id" to JsonPrimitive(id)) + ("in_reply_to_id" to (replyTo?.let(::JsonPrimitive) ?: JsonNull)) +
            extra,
    )

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath.also(asked::add)
        return body(path)?.let {
            MockResponse.Builder().code(200).body(it).addHeader("content-type", "application/json").build()
        } ?: MockResponse.Builder().code(404).body("{}").build()
    }

    private fun body(path: String): String? = when {
        "/statuses/gone" in path -> null

        path.endsWith("/favourite") -> status(
            "f",
            "a1",
            "favourited" to JsonPrimitive(true),
            "favourites_count" to JsonPrimitive(1),
        ).toString()

        path.endsWith("/statuses/f") -> {
            val focused = status("f", "a1", "edited_at" to JsonPrimitive("2026-09-29T11:00:00.000Z"))
            val reactions = Json.parseToJsonElement(if (reactionsOnStatus) ON_STATUS else "[]")
            JsonObject(if (reactionsLeftOut) focused - "reactions" else focused + ("reactions" to reactions)).toString()
        }

        path.endsWith("/statuses/p") ->
            status("p", null, "favourites_count" to JsonPrimitive(2), "reblogs_count" to JsonPrimitive(0)).toString()

        path.endsWith("/favourited_by") ->
            """[{"id":"1","username":"a","acct":"a","avatar":"https://x.test/a.png"},""" +
                """{"id":"2","username":"b","acct":"b","avatar":"https://x.test/b.png"}]"""

        path.endsWith("/context") -> JsonObject(
            mapOf(
                "ancestors" to JsonArray(listOf(status("a1", null))),
                "descendants" to JsonArray(
                    listOf(status("r1", "f"), status("r2", "r1")) +
                        if (grown) listOf(status("r3", "f")) else emptyList(),
                ),
            ),
        ).toString()

        path.endsWith("/reactions") -> """[{"name":"🎉","count":2,"me":false}]"""

        path.endsWith("/react") -> "{}"

        path.endsWith("/custom_emojis") && emojiFailures.getAndDecrement() <= 0 ->
            """[{"shortcode":"blobwave","url":"https://x.test/e/blobwave.png","visible_in_picker":true},""" +
                """{"shortcode":"stillwave","static_url":"https://x.test/e/still.png","visible_in_picker":true},""" +
                """{"shortcode":"hidden","url":"https://x.test/e/hidden.png","visible_in_picker":false}]"""

        path.endsWith("/history") -> history().toString()

        path.endsWith("/social/feed_preferences") ->
            """{"hide_replies":false${threadSort?.let { ",\"thread_sort\":\"$it\"" }.orEmpty()}}"""

        else -> null
    }

    private companion object {
        const val ON_STATUS = """[{"name":"🌊","count":1,"me":false}]"""
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
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.systemUTC()
    private val conversation = Conversation()
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
        dispatcher = conversation
        start()
    }

    @After
    fun close() {
        main.clear()
        scope.cancel()
        accountsDb.close()
        cache.close()
        server.close()
    }

    private suspend fun open(statusId: String, history: Boolean = false, version: String = ""): ThreadViewModel {
        val apiBase = server.url("/")
        val capabilities = ServerCapabilities.minimal(apiBase.toString())
            .copy(softwareName = "nextcloud-social", softwareVersion = version)
        val account = accounts.signedIn(
            NewAccount(apiBase.host, "6", "alice", "Alice", null, null, capabilities, profilePending = false),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
        return main.keep(
            ThreadViewModel(
                ThreadKey(account.id, statusId, history),
                accounts,
                ThreadRepository(statuses, clients),
                StatusInteractions(statuses, clients),
                RichTextCache(),
                clock,
                ReplyNudges(
                    AppPreferences(InMemoryDataStore(emptyPreferences())),
                    AccountSettingsStore(InMemoryDataStore(emptyMap())),
                    clients,
                    clock,
                ),
            ),
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
    fun `who favourited the focused post shows beside its count, and nobody is asked for boosts it has none of`() =
        runBlocking {
            val state = open("p").await { StatusListKind.FavouritedBy in it.people }
            assertEquals(
                listOf("https://x.test/a.png", "https://x.test/b.png"),
                state.people[StatusListKind.FavouritedBy],
            )
            assertTrue(StatusListKind.BoostedBy !in state.people)
        }

    @Test
    fun `from Social 0,26,221 the post carries its reactions, and no second request asks for them`() = runBlocking {
        conversation.reactionsOnStatus = true
        val state = open("f", version = "0.26.221").await { StatusListKind.Reactions in it.lists }
        assertEquals(1, state.lists[StatusListKind.Reactions])
        assertEquals(listOf("🌊"), state.posts().single { it.focused }.row.reactions.map { it.name })
        assertTrue(conversation.asked.none { it.endsWith("/reactions") })
    }

    @Test
    fun `a post that leaves its reactions out has them asked for, whatever the version`() = runBlocking {
        conversation.reactionsLeftOut = true
        val state = open("f", version = "0.26.251").await { StatusListKind.Reactions in it.lists }
        assertEquals(listOf("🎉"), state.posts().single { it.focused }.row.reactions.map { it.name })
    }

    @Test
    fun `reactions, which the status never carries on Nextcloud Social, come from their own route`() = runBlocking {
        val state = open("f").await { StatusListKind.Reactions in it.lists }
        assertEquals(2, state.lists[StatusListKind.Reactions])
        assertEquals(listOf("🎉"), state.posts().single { it.focused }.row.reactions.map { it.name })
    }

    @Test
    fun `from Social 0,26,280 the server's own emoji are offered, and one picked shows with its picture`() =
        runBlocking {
            val viewModel = open("f", version = "0.26.280")
            val state = viewModel.await { it.reactionEmoji.isNotEmpty() && it.posts().isNotEmpty() }
            assertEquals(listOf("blobwave", "stillwave"), state.reactionEmoji.map { it.shortcode })
            viewModel.onReact(":blobwave:", add = true)
            val reacted = viewModel.await { s -> s.posts().single { it.focused }.row.reactions.any { it.me } }
            val mine = reacted.posts().single { it.focused }.row.reactions.single { it.me }
            assertEquals(":blobwave:" to "https://x.test/e/blobwave.png", mine.name to mine.url)
        }

    @Test
    fun `replies the server ordered otherwise than oldest first say so, the order asked once per account`() =
        runBlocking {
            conversation.threadSort = "newest"
            val viewModel = open("f")
            viewModel.await { !it.loading && it.threadOrder == ThreadOrder.Newest }
            viewModel.onRefresh()
            viewModel.await { !it.loading }
            assertEquals(1, conversation.asked.count { it.endsWith("/social/feed_preferences") })
            conversation.threadSort = "oldest"
            val oldest = open("f")
            preferencesAsked(2)
            assertNull(oldest.await { !it.loading }.threadOrder)
        }

    @Test
    fun `a Social too old to report its thread order is asked again on a refresh`() = runBlocking {
        val viewModel = open("f")
        preferencesAsked(1)
        viewModel.onRefresh()
        preferencesAsked(2)
        assertNull(viewModel.await { !it.loading }.threadOrder)
    }

    /** Waits until `feed_preferences` was asked [times] times; asking it changes no state when nothing differs. */
    private suspend fun preferencesAsked(times: Int) = withTimeout(10.seconds) {
        while (conversation.asked.count { it.endsWith("/social/feed_preferences") } < times) delay(10.milliseconds)
    }

    @Test
    fun `custom emoji the server failed to give are asked again on a refresh, a still picture will do`() = runBlocking {
        conversation.emojiFailures.set(1)
        val viewModel = open("f", version = "0.26.280")
        withTimeout(10.seconds) {
            while (conversation.asked.none { it.endsWith("/custom_emojis") }) delay(10.milliseconds)
        }
        viewModel.await { !it.loading }
        viewModel.onRefresh()
        viewModel.await { it.reactionEmoji.isNotEmpty() }
        assertEquals(2, conversation.asked.count { it.endsWith("/custom_emojis") })
        viewModel.onReact(":stillwave:", add = true)
        val reacted = viewModel.await { s -> s.posts().single { it.focused }.row.reactions.any { it.me } }
        val mine = reacted.posts().single { it.focused }.row.reactions.single { it.me }
        assertEquals("https://x.test/e/still.png", mine.url)
    }

    @Test
    fun `before Social 0,26,280 no custom emoji are asked for`() = runBlocking {
        open("f", version = "0.26.279").await { !it.loading && it.posts().isNotEmpty() }
        assertTrue(conversation.asked.none { it.endsWith("/custom_emojis") })
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
        viewModel.onHistory(open = true)
        val history = checkNotNull(viewModel.await { it.history != null }.history)
        assertEquals(listOf("Edited"), history.drop(1).map { it.body.text.trim() })
        viewModel.onHistory(open = false)
        assertEquals(null, viewModel.await { it.history == null }.history)
    }

    @Test
    fun `a thread opened from an edited mark opens with the post's edits`() = runBlocking {
        val history = checkNotNull(open("f", history = true).await { it.history != null }.history)
        assertEquals(listOf("Edited"), history.drop(1).map { it.body.text.trim() })
    }

    @Test
    fun `replies a refresh finds wait behind Show, so the list stays where it is`() = runBlocking {
        val viewModel = open("f")
        viewModel.await { !it.loading && it.posts().size == 4 }
        conversation.grown = true
        viewModel.onRefresh()
        val held = viewModel.await { it.pendingReplies == 1 }
        assertEquals(listOf("a1", "f", "r1", "r2"), held.posts().map { it.row.statusId })
        viewModel.onShowReplies()
        val shown = viewModel.await { it.pendingReplies == 0 && it.posts().size == 5 }
        assertTrue("r3" in shown.posts().map { it.row.statusId })
    }

    @Test
    fun `a post the server no longer has says so`() = runBlocking {
        assertTrue(open("gone").await { it.gone }.gone)
    }
}
