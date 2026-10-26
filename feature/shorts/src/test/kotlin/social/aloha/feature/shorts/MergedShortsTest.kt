// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.AllAccounts
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.timeline.ModeFeeds
import social.aloha.core.data.timeline.ModeSources
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AccountViewPreferences
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.network.RateLimiter
import social.aloha.core.sync.DeviceConditions
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.isElsewhere

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MergedShortsTest {
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

    // the first account's post n is a short at minute n, its Social narrowing to shorts itself; the second,
    // on a server that does not narrow, sees the first's even ones too and has odd ones of its own at the
    // same minutes, every other of them a landscape video the device leaves out
    private val first = NumberedTimeline().apply {
        newest = 100
        narrows = true
        fields = { stamped("https://other.social/$it", it) }
        media = { listOf(NumberedTimeline.video(it, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, SECONDS)) }
    }
    private val second = NumberedTimeline().apply {
        newest = 100
        fields = { stamped(if (it % 2 == 0) "https://other.social/$it" else "https://second.social/$it", it) }
        media = {
            val wide = it % LANDSCAPE_EVERY == LANDSCAPE_AT
            val (width, height) = if (wide) PORTRAIT_HEIGHT to PORTRAIT_WIDTH else PORTRAIT_WIDTH to PORTRAIT_HEIGHT
            listOf(NumberedTimeline.video(it, width, height, SECONDS))
        }
    }
    private val asked = CopyOnWriteArrayList<String>()
    private val firstServer = MockWebServer().apply { dispatcher = recording("first", first) }
    private val secondServer = MockWebServer().apply { dispatcher = recording("second", second) }
    private lateinit var viewModel: ShortsViewModel
    private lateinit var secondPrefix: String

    private fun stamped(uri: String, minute: Int) = mapOf(
        "uri" to JsonPrimitive(uri),
        "created_at" to JsonPrimitive(NOON.plusSeconds(minute * 60L).toString()),
    )

    private fun recording(name: String, timeline: NumberedTimeline) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = timeline.dispatch(request).also {
            asked += "$name ${request.method} ${request.url.encodedPath}?${request.url.query.orEmpty()}"
        }
    }

    @Before
    fun create() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        firstServer.start()
        secondServer.start()
        signIn(firstServer, "6", onlyShort = true)
        // accounts are in the order they were added, which a shared millisecond leaves open
        delay(ADDED_APART_MILLIS)
        signIn(secondServer, "7", onlyShort = false)
        secondPrefix = "remote:${secondServer.url("/").host}:"
        val allAccounts = AllAccounts(accounts, AccountViewPreferences(InMemoryDataStore(emptyPreferences())))
        val modes = ModeSources(accounts, AccountSettingsStore(InMemoryDataStore(emptyMap())), allAccounts)
        val timelines = TimelineRepository(cache.timelineDao(), statuses, clients, accounts, clock, Dispatchers.IO)
        viewModel = ShortsViewModel(
            together = true,
            ModeFeeds(timelines, modes, clock),
            modes,
            ModePreferences(InMemoryDataStore(emptyPreferences())),
            StatusInteractions(statuses, clients),
            RichTextCache(),
            Unmetered,
        ).apply { onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray)) }
    }

    private suspend fun signIn(server: MockWebServer, remoteId: String, onlyShort: Boolean) {
        val apiBase = server.url("/")
        accounts.signedIn(
            NewAccount(
                apiBase.host,
                remoteId,
                "alice$remoteId",
                "Alice",
                null,
                null,
                ServerCapabilities.minimal(apiBase.toString()).copy(onlyShortFilter = onlyShort),
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

    private suspend fun loaded(more: Int = 0): ShortsUiState = withTimeout(10.seconds) {
        viewModel.uiState.first { state ->
            state.loadedOnce && state.shorts.size > more &&
                state.shorts.map { isElsewhere(it.row.statusId) }.toSet().size == 2
        }
    }

    /** The post number each short shows, the second's own marked as such. */
    private fun ShortsUiState.numbers() = shorts.map { it.row.statusId.replace(secondPrefix, "second-") }

    @Test
    fun `every account's shorts show newest first, a shared one once, each server narrowing as it can`() = runBlocking {
        val state = loaded()
        val numbers = state.numbers()
        val expected = (100 downTo 1).flatMap {
            when {
                it % 2 == 0 -> listOf("$it")
                it % LANDSCAPE_EVERY == LANDSCAPE_AT -> listOf("$it")
                else -> listOf("$it", "second-$it")
            }
        }
        assertEquals(expected.take(numbers.size), numbers)
        assertTrue(numbers.size > PAGE)
        assertEquals(2, state.shorts.first { it.row.statusId == "100" }.seenBy.size)
        assertEquals(1, state.shorts.first { it.row.statusId == "${secondPrefix}97" }.seenBy.size)
        assertTrue(state.shorts.first { isElsewhere(it.row.statusId) }.row.author.id.startsWith("remote:"))
        val timelines = asked.filter { "/api/v1/timelines/home" in it }
        val firsts = timelines.filter { it.startsWith("first") }
        assertTrue(firsts.isNotEmpty() && firsts.all { "only_short=true" in it })
        assertTrue(timelines.filter { it.startsWith("second") }.none { "only_short" in it })
        assertTrue(state.sources.size <= 1)
    }

    @Test
    fun `a like on a short only the second account holds goes to the second's server, on its own id`() = runBlocking {
        loaded()
        viewModel.onToggle("${secondPrefix}97", Toggle.Favourite)
        withTimeout(10.seconds) {
            while (asked.none { it.startsWith("second POST /api/v1/statuses/97/favourite") }) delay(POLL_MILLIS)
        }
        assertFalse(asked.any { it.startsWith("first POST") })
    }

    @Test
    fun `scrolling on adds the next page below, leaving every short above where it was`() = runBlocking {
        val before = loaded().numbers()
        viewModel.onNearEnd()
        val after = loaded(more = before.size).numbers()
        assertEquals(before, after.take(before.size))
        assertEquals(after, after.distinct())
    }

    private object Unmetered : DeviceConditions {
        override val online: StateFlow<Boolean> = MutableStateFlow(true)
        override val metered: Boolean = false
        override val powerSave: Boolean = false
    }

    private companion object {
        const val ADDED_APART_MILLIS = 5L
        val NOON: Instant = Instant.parse("2026-10-11T12:00:00Z")
        const val POLL_MILLIS = 50L
        const val PAGE = 20
        const val PORTRAIT_WIDTH = 720
        const val PORTRAIT_HEIGHT = 1280
        const val SECONDS = 120.0
        const val LANDSCAPE_EVERY = 4
        const val LANDSCAPE_AT = 3
    }
}
