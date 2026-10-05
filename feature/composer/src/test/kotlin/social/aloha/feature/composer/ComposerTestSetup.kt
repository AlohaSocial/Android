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
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.PostSender
import social.aloha.core.data.compose.ScheduledPosts
import social.aloha.core.data.stories.Stories
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.database.OutboxDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.IntelligencePreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.intelligence.Intelligence
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.Writing
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
internal class Posts(private val script: MutableList<Int>) : Dispatcher() {
    val sent = CopyOnWriteArrayList<Pair<String?, Map<String, String>>>()
    val stories = CopyOnWriteArrayList<Map<String, String>>()
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

            path.endsWith("/search") -> json("""{"accounts":[],"hashtags":[],"statuses":[$parent]}""")

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

            else -> story(request)
        }
    }

    /** A story shared, remembered; anything else is not here. */
    private fun story(request: RecordedRequest): MockResponse {
        if (request.method != "POST" || !request.url.encodedPath.endsWith("/api/v1/stories")) {
            return MockResponse.Builder().code(404).body("{}").build()
        }
        stories += form(request)
        return json("{}")
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

/** A composer against a mock server that records what it is sent, for the composer's tests. */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class ComposerTestSetup {
    protected val clock = Clock.systemUTC()
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    protected val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    protected val accountsDb = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    protected val cache = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    protected val accounts = AccountRepository(
        accountsDb.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        clock,
        scope,
    )
    protected val clients =
        ClientFactory(OkHttpClient(), RateLimiter(nowMillis = clock::millis), Dispatchers.IO, accounts)
    protected val statuses = StatusRepository(cache.statusDao(), clock)
    protected val script = mutableListOf<Int>()
    protected val posts = Posts(script)
    protected val server = MockWebServer().apply {
        dispatcher = posts
        start()
    }

    // nothing clears the view models, so their work (a draft kept a moment later) is stopped here,
    // before the main dispatcher it runs on is taken away
    protected val opened = CopyOnWriteArrayList<ComposerViewModel>()

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

    protected val outboxDb = Room.inMemoryDatabaseBuilder(context, OutboxDatabase::class.java).build()
    protected val outbox = Outbox(outboxDb.outboxDao(), clock)

    /** The on-device features as a build without them has them; a test about them sets its own. */
    protected var intelligence = Intelligence(context, Optional.empty(), Optional.empty())

    protected suspend fun open(
        writing: Writing = Writing(),
        key: (String) -> ComposerKey = { ComposerKey(it) },
        software: String = "nextcloud-social",
    ): ComposerViewModel {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val choices = IntelligencePreferences(InMemoryDataStore(emptyPreferences()))
        choices.update { it.copy(altText = true) }
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val apiBase = server.url("/")
        val capabilities = ServerCapabilities.minimal(apiBase.toString()).copy(
            softwareName = software,
            limits = ServerLimits.MastodonDefaults.copy(maxStatusCharacters = 5000),
            stories = true,
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
            AppPreferences(InMemoryDataStore(emptyPreferences())).also { it.setWriting(writing) },
            Stories(clients, clock),
            intelligence,
            choices,
        ).also(opened::add)
    }

    protected suspend fun ComposerViewModel.await(predicate: (ComposerUiState) -> Boolean): ComposerUiState =
        withTimeout(10.seconds) { uiState.first(predicate) }

    // outside a composition nothing sends snapshot changes on; the test does, as a frame would
    protected fun ComposerViewModel.type(index: Int, text: String) {
        onText(index, TextFieldValue(text, TextRange(text.length)))
        Snapshot.sendApplyNotifications()
    }
}
