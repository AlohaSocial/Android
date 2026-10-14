// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
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
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.AllAccounts
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelinePositions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AccountViewPreferences
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AccessToken
import social.aloha.core.model.FeedMode
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline
import social.aloha.core.ui.RichTextColors

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MergedTimelineViewModelTest {
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

    // the first account's post n is the post at minute n; the second sees its even ones too, and has odd
    // ones of its own at the same minutes
    private val first = NumberedTimeline().apply {
        newest = 100
        fields = { stamped("https://other.social/$it", it) }
    }
    private val second = NumberedTimeline().apply {
        newest = 100
        fields = { stamped(if (it % 2 == 0) "https://other.social/$it" else "https://second.social/$it", it) }
    }
    private val asked = CopyOnWriteArrayList<String>()
    private val firstServer = MockWebServer().apply { dispatcher = recording("first", first) }
    private val secondServer = MockWebServer().apply { dispatcher = recording("second", second) }
    private lateinit var viewModel: MergedTimelineViewModel
    private lateinit var secondHost: String

    private fun stamped(uri: String, minute: Int) = mapOf(
        "uri" to JsonPrimitive(uri),
        "created_at" to JsonPrimitive(NOON.plusSeconds(minute * 60L).toString()),
    )

    private fun recording(name: String, timeline: NumberedTimeline) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = timeline.dispatch(request).also {
            asked += "$name ${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}"
        }
    }

    @Before
    fun create() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        firstServer.start()
        secondServer.start()
        signIn(firstServer, "6")
        signIn(secondServer, "7")
        secondHost = secondServer.url("/").host
        viewModel = create(TimelineFeed.Pinned(TimelineSource.Home))
    }

    private fun create(feed: TimelineFeed) = MergedTimelineViewModel(
        feed,
        AllAccounts(accounts, AccountViewPreferences(InMemoryDataStore(emptyPreferences()))),
        TimelineRepository(cache.timelineDao(), statuses, clients, accounts, clock, Dispatchers.IO),
        MergedRows {
            TimelineRowBuilder(
                RichTextCache(),
                FilterRepository(cache.filterDao(), clients),
                FollowedAuthors(clients),
                clock,
            )
        },
        StatusInteractions(statuses, clients),
        TimelinePositions(cache.positionDao(), clients),
        AccountSettingsStore(InMemoryDataStore(emptyMap())),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        ReadingPreferences(InMemoryDataStore(emptyPreferences())),
        ModePreferences(InMemoryDataStore(emptyPreferences())),
        WatchPositions(clients, cache.watchPositionDao(), statuses, clock),
        clock,
        TimelineSignals(),
    ).apply { onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray)) }

    private suspend fun signIn(server: MockWebServer, remoteId: String) {
        val apiBase = server.url("/")
        accounts.signedIn(
            NewAccount(
                apiBase.host,
                remoteId,
                "alice$remoteId",
                "Alice",
                null,
                null,
                ServerCapabilities.minimal(apiBase.toString()),
                profilePending = false,
            ),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        scope.cancel()
        accountsDb.close()
        cache.close()
        firstServer.close()
        secondServer.close()
    }

    private suspend fun loaded(rows: Int = 30): TimelineUiState =
        withTimeout(10.seconds) { viewModel.uiState.first { it.loadedOnce && it.items.size == rows } }

    private fun TimelineUiState.post(id: String) =
        items.filterIsInstance<TimelineItem.Post>().single { it.row.statusId == id }

    @Test
    fun `every account's posts show together, a shared one once, the second's own under its server's name`() =
        runBlocking {
            val state = loaded()
            val shared = state.post("90")
            assertEquals(2, shared.seenBy.size)
            val own = state.post("${TimelineSource.Remote.ID_PREFIX}$secondHost:99")
            assertEquals(1, own.seenBy.size)
            assertTrue(own.row.author.id.startsWith(TimelineSource.Remote.ID_PREFIX))
            assertEquals(
                (100 downTo 81).flatMap { if (it % 2 == 0) listOf("$it") else listOf("$it", "second-$it") },
                state.items.filterIsInstance<TimelineItem.Post>().map {
                    it.row.statusId.replace("${TimelineSource.Remote.ID_PREFIX}$secondHost:", "second-")
                },
            )
        }

    @Test
    fun `a post only the second account sees is favourited as that account, on its own server`() = runBlocking {
        loaded()
        viewModel.onToggle("${TimelineSource.Remote.ID_PREFIX}$secondHost:99", Toggle.Favourite)
        withTimeout(10.seconds) {
            while (asked.none { it.startsWith("second POST /api/v1/statuses/99/favourite") }) delay(POLL_MILLIS)
        }
        assertTrue(asked.none { it.startsWith("first POST /api/v1/statuses/99/favourite") })
    }

    @Test
    fun `reading down to a post moves each account's read marker to its newest post no newer`() = runBlocking {
        second.newest = 98
        second.fields = { stamped("https://second.social/$it", it - 5) }
        loaded(rows = 33)
        // the second's post 89 came at minute 84: the first has read down to its 84, the second to its 89
        viewModel.onScrolled("${TimelineSource.Remote.ID_PREFIX}$secondHost:89", 0)
        withTimeout(10.seconds) {
            while (asked.count { " POST /api/v1/markers" in it } < 2) delay(POLL_MILLIS)
        }
        val markers = asked.filter { " POST /api/v1/markers" in it }.associate { it.substringBefore(' ') to marked(it) }
        assertEquals(mapOf("first" to "84", "second" to "89"), markers)
    }

    @Test
    fun `Photos shows every account's pictures together, as a feed, and moves no read marker`() = runBlocking {
        first.media = { listOf(NumberedTimeline.image(it)) }
        second.media = { listOf(NumberedTimeline.image(it)) }
        val photos = create(TimelineFeed.Mode(FeedMode.Photos))
        val state = withTimeout(10.seconds) { photos.uiState.first { it.loadedOnce && it.items.size == 30 } }
        assertEquals(false, state.grid)
        photos.onScrolled(state.items.first().key, 0)
        delay(SETTLE_MILLIS)
        assertTrue(asked.none { " POST /api/v1/markers" in it })
    }

    /** The home read marker a recorded request sets. */
    private fun marked(request: String) = MARKER.find(request)?.groupValues?.get(1)

    private companion object {
        val NOON: Instant = Instant.parse("2026-10-11T12:00:00Z")
        const val POLL_MILLIS = 50L
        const val SETTLE_MILLIS = 1_000L
        val MARKER = Regex("""last_read_id(?:%5D|])=(\d+)""")
    }
}
