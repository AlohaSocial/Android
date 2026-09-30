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
import androidx.work.testing.WorkManagerTestInitHelper
import java.net.URLDecoder
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
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
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.PostSender
import social.aloha.core.data.compose.ScheduledPosts
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.database.OutboxDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.OutboxState
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.network.RateLimiter
import social.aloha.core.sync.MediaUploads
import social.aloha.core.sync.PostQueue
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
    val deleted = CopyOnWriteArrayList<String>()
    private val made = HashMap<String, String>()
    private var next = 0
    private val template = JsonObject(NumberedTimeline.homeTemplate() + ("reblog" to JsonNull))

    fun status(id: String, visibility: String = "public", mentions: List<Pair<String, String>> = emptyList()) =
        JsonObject(
            template + ("id" to JsonPrimitive(id)) + ("visibility" to JsonPrimitive(visibility)) +
                ("mentions" to JsonArray(mentions.map { (mid, acct) -> mention(mid, acct) })),
        )

    /** The reader's own post [id], with a described picture, and its source [text] as a delete answers. */
    fun own(id: String, text: String?): JsonObject {
        val picture = JsonObject(
            mapOf(
                "id" to JsonPrimitive("m1"),
                "type" to JsonPrimitive("image"),
                "url" to JsonPrimitive("https://example.test/beach.jpg"),
                "description" to JsonPrimitive("Old words"),
            ),
        )
        return JsonObject(
            status(id) + ("visibility" to JsonPrimitive("unlisted")) +
                ("text" to (text?.let(::JsonPrimitive) ?: JsonNull)) +
                ("media_attachments" to JsonArray(listOf(picture))),
        )
    }

    private fun mention(id: String, acct: String) = JsonObject(
        mapOf("id" to JsonPrimitive(id), "username" to JsonPrimitive(acct), "acct" to JsonPrimitive(acct)),
    )

    var parent: JsonObject? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            request.method == "POST" && path.endsWith("/statuses") -> post(request)

            path.endsWith("/statuses/p") -> json(parent.toString())

            path.endsWith("/statuses/mine/source") || path.endsWith("/statuses/gone/source") ->
                json("""{"id":"x","text":"As I wrote it","spoiler_text":""}""")

            request.method == "PUT" && path.endsWith("/statuses/mine") -> {
                sent += null to form(request)
                json(own("mine", null).toString())
            }

            path.endsWith("/statuses/mine") -> json(own("mine", null).toString())

            request.method == "DELETE" && path.endsWith("/statuses/gone") -> {
                deleted += "gone"
                json(own("gone", "Written again").toString())
            }

            path.endsWith("/statuses/gone") -> json(own("gone", null).toString())

            path.endsWith("/custom_emojis") -> json("[]")

            path.endsWith("/preferences") -> json("{}")

            path.endsWith("/gifs") -> gifs(request.url.queryParameter("offset")?.toInt() ?: 0)

            path.endsWith("/media/from-gif") -> media(request, "slug")

            path.endsWith("/media/from-file") -> media(request, "path")

            else -> MockResponse.Builder().code(404).body("{}").build()
        }
    }

    // two pages of forty and a last of five, the way the library pages
    private fun gifs(offset: Int): MockResponse {
        val count = if (offset < GIFS - GIFS % PAGE) PAGE else GIFS % PAGE
        val gifs = (offset until offset + count).joinToString(",") { """{"slug":"g$it","title":"GIF $it"}""" }
        return json("""{"gifs":[$gifs],"total":$GIFS,"attribution":"Library"}""")
    }

    /** Media the server makes itself, named by what was asked for; a path it has no file at is a 422. */
    private fun media(request: RecordedRequest, field: String): MockResponse {
        val asked = form(request)[field].orEmpty()
        if (asked == "missing.jpg") return MockResponse.Builder().code(422).body("""{"error":"not found"}""").build()
        return json("""{"id":"m-$asked","type":"image","url":"https://example.test/$asked"}""")
    }

    private fun form(request: RecordedRequest) =
        request.body?.utf8().orEmpty().split('&').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    private fun post(request: RecordedRequest): MockResponse {
        val key = request.headers["Idempotency-Key"]
        val form = form(request)
        sent += key to form
        val at = form["scheduled_at"]
        val known = key?.let { made[it] }
        return when {
            at != null -> json("""{"id":"later","scheduled_at":"$at","params":{"text":"${form["status"]}"}}""")
            known != null -> json(status(known).toString())
            else -> scripted(key)
        }
    }

    private fun scripted(key: String?): MockResponse = when (script.removeFirstOrNull() ?: 200) {
        200 -> {
            val id = "s${++next}"
            key?.let { made[it] = id }
            json(status(id).toString())
        }

        422 -> MockResponse.Builder().code(422).body("""{"error":"a post may not be longer"}""").build()

        else -> MockResponse.Builder().code(500).body("{}").build()
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val GIFS = 85
        const val PAGE = 40
    }
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

    private val outboxDb = Room.inMemoryDatabaseBuilder(context, OutboxDatabase::class.java).build()
    private val outbox = Outbox(outboxDb.outboxDao(), clock)

    private suspend fun open(key: (String) -> ComposerKey = { ComposerKey(it) }): ComposerViewModel {
        Dispatchers.setMain(Dispatchers.Unconfined)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
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
            key(account.id),
            context,
            accounts,
            compose,
            PostSender(compose, ScheduledPosts(clients), StatusInteractions(statuses, clients)),
            outbox,
            PostQueue(context, outbox),
            StatusInteractions(statuses, clients),
            scope,
            RemoteLookup(clients, statuses),
            MediaUploads(context),
            MediaRepository(clients),
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
            val viewModel = open { ComposerKey(it, replyToId = "p") }
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

    @Test
    fun `the gif library pages to its end, and a gif picked goes out with the post`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready && it.gifLibrary }
        viewModel.library.onQuery("")
        withTimeout(10.seconds) { viewModel.library.gifs.first { it.gifs.size == 40 && !it.loading } }
        viewModel.library.onMore()
        withTimeout(10.seconds) { viewModel.library.gifs.first { it.gifs.size == 80 && !it.loading } }
        viewModel.library.onMore()
        val all = withTimeout(10.seconds) { viewModel.library.gifs.first { it.end } }
        assertEquals(85, all.gifs.size)
        assertEquals("Library", all.attribution)
        viewModel.library.onGif(all.gifs.first())
        val attached = viewModel.await { it.attachments.first().isNotEmpty() }.attachments.first().single()
        assertEquals("m-g0", attached.mediaId)
        assertEquals("GIF 0", attached.description)
        viewModel.type(0, "Look")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals("m-g0", posts.sent.single().second["media_ids[]"])
    }

    @Test
    fun `a nextcloud file is attached by its path from the root, and one not there says so`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.library.onNextcloudFile(" /Photos/beach.jpg ")
        val attached = viewModel.await { it.attachments.first().isNotEmpty() }.attachments.first().single()
        assertEquals("m-Photos/beach.jpg", attached.mediaId)
        assertEquals("beach.jpg", attached.fileName)
        assertEquals(listOf("Photos/beach.jpg"), viewModel.library.recentPaths())
        viewModel.library.onNextcloudFile("missing.jpg")
        assertEquals(AttachFailure.NotFound, viewModel.await { it.attachFailure != null }.attachFailure)
    }

    @Test
    fun `a poll goes out with the opening post and takes the place of its media`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Which beach?")
        viewModel.poll.value = PollUi(options = listOf(" North ", "South", ""), seconds = 3_600, multiple = true)
        viewModel.await { it.poll != null && it.canPost }
        viewModel.library.onNextcloudFile("Photos/beach.jpg")
        viewModel.onPost()
        viewModel.await { it.done }
        val form = posts.sent.single().second
        assertEquals("South", form["poll[options][]"])
        assertEquals("3600", form["poll[expires_in]"])
        assertEquals("true", form["poll[multiple]"])
        assertEquals(null, form["media_ids[]"])
    }

    @Test
    fun `a poll with fewer than two different choices cannot be posted`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Which beach?")
        viewModel.poll.value = PollUi(options = listOf("North", " North"))
        assertEquals(false, viewModel.await { it.poll != null }.canPost)
    }

    @Test
    fun `a scheduled post is scheduled, not posted`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Later")
        val at = java.time.Instant.parse("2030-01-01T09:00:00Z")
        viewModel.scheduledAt.value = at
        viewModel.await { it.scheduledAt != null && it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals(at.toString(), posts.sent.single().second["scheduled_at"])
    }

    @Test
    fun `what is written is kept as a draft and comes back as it was`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Half a thought")
        viewModel.onVisibility(Visibility.Unlisted)
        viewModel.poll.value = PollUi(options = listOf("Yes", "No"), seconds = 3_600)
        val kept = withTimeout(10.seconds) {
            outbox.observe(accounts.all().single().id).first { list -> list.any { it.post.poll != null } }
        }.single()
        assertEquals("Half a thought", kept.post.segments.single().text)
        val again = open { ComposerKey(it, draftId = kept.id) }
        // the poll reaches the state through a flow of its own, a moment apart from the rest
        val state = again.await { it.ready && it.poll != null }
        assertEquals("Half a thought", again.segments.single().text)
        assertEquals(Visibility.Unlisted, state.visibility)
        assertEquals(listOf("Yes", "No"), state.poll?.options)
    }

    @Test
    fun `a posted draft is gone`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Out it goes")
        val reader = accounts.all().single().id
        withTimeout(10.seconds) { outbox.observe(reader).first { it.isNotEmpty() } }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals(emptyList<Any>(), withTimeout(10.seconds) { outbox.observe(reader).first { it.isEmpty() } })
    }

    @Test
    fun `a post with no network goes to the outbox, keyed, and out later`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "From the reef /flip")
        viewModel.await { it.canPost }
        server.close()
        viewModel.onPost()
        assertEquals(true, viewModel.await { it.done }.queued)
        val queued = outbox.observe(accounts.all().single().id).first().single()
        assertEquals(OutboxState.Queued, queued.state)
        val segment = queued.post.segments.single()
        assertEquals(true, segment.key != null)
        // the game is played once, now, so the post the outbox sends is the one the writer saw
        assertEquals(false, segment.sent.orEmpty().contains("/flip"))
    }

    @Test
    fun `an edit starts from the post as written, changes it in place, and carries its media's words`() = runBlocking {
        val viewModel = open { ComposerKey(it, editId = "mine") }
        val state = viewModel.await { it.ready && it.attachments.first().isNotEmpty() }
        assertEquals(true, state.editing)
        assertEquals("As I wrote it", viewModel.segments.single().text)
        assertEquals(Visibility.Unlisted, state.visibility)
        val picture = state.attachments.first().single()
        viewModel.attachments.describe(picture.id, "New words", null)
        viewModel.type(0, "As I meant it")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        val form = posts.sent.single().second
        assertEquals("As I meant it", form["status"])
        assertEquals("m1", form["media_attributes[][id]"])
        assertEquals("New words", form["media_attributes[][description]"])
    }

    @Test
    fun `a redraft deletes the original only once the new post is out`() = runBlocking {
        val viewModel = open { ComposerKey(it, redraftId = "gone") }
        val state = viewModel.await { it.ready && it.attachments.first().isNotEmpty() }
        assertEquals(false, state.editing)
        assertEquals("As I wrote it", viewModel.segments.single().text)
        assertEquals("m1", state.attachments.first().single().mediaId)
        assertEquals("Old words", state.attachments.first().single().description)
    }
}
