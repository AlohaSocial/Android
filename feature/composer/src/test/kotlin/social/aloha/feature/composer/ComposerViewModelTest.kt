// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.net.URLDecoder
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
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
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.network.RateLimiter
import social.aloha.core.sync.MediaUploads
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline

/**
 * A server that takes posts as a script says: each `POST /statuses` answers with the next outcome
 * (a status numbered in order, a 500, or a 422), and remembers what was sent and under which key.
 * An idempotency key it has seen answers with the post it made then, as Nextcloud Social does.
 */
private class Posts(private val script: MutableList<Int>) : Dispatcher() {
    val sent = CopyOnWriteArrayList<Pair<String?, Map<String, String>>>()
    private val made = HashMap<String, String>()
    private var next = 0
    private val template = JsonObject(NumberedTimeline.homeTemplate() + ("reblog" to JsonNull))

    fun status(id: String, visibility: String = "public", mentions: List<Pair<String, String>> = emptyList()) =
        JsonObject(
            template + ("id" to JsonPrimitive(id)) + ("visibility" to JsonPrimitive(visibility)) +
                ("mentions" to JsonArray(mentions.map { (mid, acct) -> mention(mid, acct) })),
        )

    private fun mention(id: String, acct: String) = JsonObject(
        mapOf("id" to JsonPrimitive(id), "username" to JsonPrimitive(acct), "acct" to JsonPrimitive(acct)),
    )

    var parent: JsonObject? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            request.method == "POST" && path.endsWith("/statuses") -> post(request)
            path.endsWith("/statuses/p") -> json(parent.toString())
            path.endsWith("/custom_emojis") -> json("[]")
            path.endsWith("/preferences") -> json("{}")
            else -> MockResponse.Builder().code(404).body("{}").build()
        }
    }

    private fun post(request: RecordedRequest): MockResponse {
        val key = request.headers["Idempotency-Key"]
        val form = request.body?.utf8().orEmpty().split('&').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
        sent += key to form
        key?.let { made[it] }?.let { return json(status(it).toString()) }
        return when (script.removeFirstOrNull() ?: 200) {
            200 -> {
                val id = "s${++next}"
                key?.let { made[it] = id }
                json(status(id).toString())
            }

            422 -> MockResponse.Builder().code(422).body("""{"error":"a post may not be longer"}""").build()

            else -> MockResponse.Builder().code(500).body("{}").build()
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ComposerViewModelTest {
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
    private val script = mutableListOf<Int>()
    private val posts = Posts(script)
    private val server = MockWebServer().apply {
        dispatcher = posts
        start()
    }

    // nothing clears the view models, so their work (a draft kept a moment later) is stopped here,
    // before the main dispatcher it runs on is taken away
    private val opened = CopyOnWriteArrayList<ComposerViewModel>()

    @After
    fun close() {
        opened.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        scope.cancel()
        accountsDb.close()
        outboxDb.close()
        cache.close()
        server.close()
    }

    private suspend fun open(replyToId: String? = null): ComposerViewModel {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val apiBase = server.url("/")
        val capabilities = ServerCapabilities.minimal(apiBase.toString()).copy(
            softwareName = "nextcloud-social",
            limits = ServerLimits.MastodonDefaults.copy(maxStatusCharacters = 5000),
        )
        val account = accounts.signedIn(
            NewAccount(apiBase.host, "6", "alice", "Alice", null, null, capabilities, profilePending = false),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
        val timelines = TimelineRepository(cache.timelineDao(), statuses, clients, accounts, clock, Dispatchers.IO)
        val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
        val compose = ComposeRepository(clients, statuses, timelines, settings, clock, scope)
        return ComposerViewModel(
            ComposerKey(account.id, replyToId),
            context,
            accounts,
            compose,
            RemoteLookup(clients, statuses),
            MediaUploads(context),
            clients,
            AppPreferences(InMemoryDataStore(emptyPreferences())),
        ).also(opened::add)
    }

    private suspend fun ComposerViewModel.await(predicate: (ComposerUiState) -> Boolean): ComposerUiState =
        withTimeout(10.seconds) { uiState.first(predicate) }

    // outside a composition nothing sends snapshot changes on; the test does, as a frame would
    private fun ComposerViewModel.type(index: Int, text: String) {
        onText(index, TextFieldValue(text, TextRange(text.length)))
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `a reply starts with its author and everyone it mentioned, never oneself, and reaches no further`() =
        runBlocking {
            posts.parent = posts.status("p", visibility = "private", mentions = listOf("6" to "alice", "8" to "carol"))
            val viewModel = open(replyToId = "p")
            val state = viewModel.await { it.ready }
            assertEquals(listOf(Visibility.Private, Visibility.Direct), state.visibilities)
            assertTrue(state.visibilityClamped)
            assertEquals(Visibility.Private, state.visibility)
            assertTrue(viewModel.segments[0].text.endsWith("@carol "))
            assertTrue("@alice" !in viewModel.segments[0].text)
        }

    @Test
    fun `a thread that fails part way resumes without posting anything twice`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "one")
        viewModel.onSegments()
        viewModel.type(1, "two")
        viewModel.onSegments()
        viewModel.type(2, "three")
        viewModel.await { it.remaining.size == 3 }
        script += listOf(200, 500)
        viewModel.onPost()
        val stopped = viewModel.await { !it.posting && it.failure != null }
        assertEquals(1, stopped.posted)
        assertTrue(stopped.failure is PostFailure.Unreached)

        viewModel.onPost()
        viewModel.await { it.done }
        val texts = posts.sent.map { it.second["status"] }
        assertEquals(listOf("one", "two", "two", "three"), texts)
        // the retried segment went with the key it had the first time, and answered the one before it
        assertEquals(posts.sent[1].first, posts.sent[2].first)
        assertEquals(listOf(null, "s1", "s1", "s2"), posts.sent.map { it.second["in_reply_to_id"] })
    }

    @Test
    fun `games are played once and sent as text, and a changed post gets a new key`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "/flip")
        assertEquals(listOf(ComposerGames.Kind.Flip), viewModel.await { it.games.isNotEmpty() }.games)
        script += 500
        viewModel.onPost()
        viewModel.await { !it.posting && it.failure != null }
        viewModel.onPost()
        viewModel.await { it.done }
        val (first, second) = posts.sent
        assertEquals(first.second["status"], second.second["status"])
        assertTrue(first.second["status"]!!.startsWith("🪙 "))
        assertEquals(first.first, second.first)
    }

    @Test
    fun `a post the server refuses keeps its text and says why`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "too long, says the server")
        viewModel.await { it.canPost }
        script += 422
        viewModel.onPost()
        val state = viewModel.await { it.failure != null }
        assertEquals(PostFailure.Refused("a post may not be longer"), state.failure)
        assertEquals("too long, says the server", viewModel.segments[0].text)
        assertEquals(0, state.posted)
    }

    @Test
    fun `nextcloud social counts what it enforces, code points at full length`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "👨‍👩‍👧‍👦 https://example.test/a/long/path")
        val state = viewModel.await { it.remaining.first() < 5000 }
        assertEquals(5000 - 7 - 1 - 32, state.remaining.first())
    }
}
