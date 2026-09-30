// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
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
import mockwebserver3.MockWebServer
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
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelinePositions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.html.RichTextCache
import social.aloha.core.media.ImagePrefetcher
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineKey
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
class TimelineViewModelTest {
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
    private val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
    private val preferences = AppPreferences(InMemoryDataStore(emptyPreferences()))
    private val signals = TimelineSignals()
    private val timeline = NumberedTimeline().apply { newest = 100 }
    private val server = MockWebServer().apply { dispatcher = timeline }
    private lateinit var viewModel: TimelineViewModel

    @Before
    fun create() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server.start()
        val apiBase = server.url("/")
        accounts.signedIn(
            NewAccount(
                apiBase.host,
                "6",
                "alice",
                "Alice",
                null,
                null,
                ServerCapabilities.minimal(apiBase.toString()),
                profilePending = false,
            ),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
        viewModel = create(TimelineFeed.Home)
    }

    private fun create(feed: TimelineFeed) = TimelineViewModel(
        feed,
        accounts,
        TimelineRepository(cache.timelineDao(), statuses, clients, accounts, clock, Dispatchers.IO),
        TimelineRowBuilder(RichTextCache(), FilterRepository(cache.filterDao(), clients), clock),
        StatusInteractions(statuses, clients),
        TimelinePositions(cache.positionDao(), clients),
        settings,
        preferences,
        clock,
        ApplicationProvider.getApplicationContext<Context>().let { ImagePrefetcher(it, ImageLoader(it)) },
        signals,
    ).apply { onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray)) }

    @After
    fun close() {
        Dispatchers.resetMain()
        scope.cancel()
        accountsDb.close()
        cache.close()
        server.close()
    }

    private suspend fun await(predicate: (TimelineUiState) -> Boolean): TimelineUiState =
        withTimeout(10.seconds) { viewModel.uiState.first(predicate) }

    private fun TimelineUiState.postIds() = items.filterIsInstance<TimelineItem.Post>().map { it.row.statusId }

    private fun TimelineUiState.row(id: String) = items.filterIsInstance<TimelineItem.Post>().single {
        it.row.statusId ==
            id
    }.row

    @Test
    fun `the first open shows the newest page, with nothing held back`() = runBlocking {
        val state = await { it.loadedOnce && it.items.isNotEmpty() }
        assertEquals((100 downTo 81).map(Int::toString), state.postIds())
        assertEquals(0, state.pending)
        assertEquals(null, state.trouble)
    }

    @Test
    fun `posts a refresh brings wait behind the pill, and revealing them scrolls to the top`() = runBlocking {
        await { it.loadedOnce && it.items.isNotEmpty() }
        timeline.newest = 103
        viewModel.onRefresh()
        val held = await { it.pending == 3 }
        assertEquals("100", held.postIds().first())
        viewModel.onRevealPending()
        val shown = await { it.pending == 0 && it.scrollToTop }
        assertEquals(listOf("103", "102", "101", "100"), shown.postIds().take(4))
        viewModel.onScrolledToTop()
        assertFalse(await { !it.scrollToTop }.scrollToTop)
    }

    @Test
    fun `a timeline on screen refreshes when a poll says it is due, and holds what came behind the pill`() =
        runBlocking {
            await { it.loadedOnce && it.items.isNotEmpty() }
            val account = accounts.activeAccount.value!!
            viewModel.onShown(isShown = true)
            assertTrue(signals.onScreen(account.id))
            timeline.newest = 102
            signals.markDue(account.id)
            assertEquals("100", await { it.pending == 2 }.postIds().first())
            viewModel.onShown(isShown = false)
            // one off screen is not kept fresh: it refreshes when it is shown again
            assertFalse(signals.onScreen(account.id))
        }

    @Test
    fun `nearing the end loads the next page below`() = runBlocking {
        await { it.loadedOnce && it.items.isNotEmpty() }
        viewModel.onNearEnd()
        val state = await { it.postIds().size == 40 && !it.loadingOlder }
        assertEquals("61", state.postIds().last())
    }

    @Test
    fun `a favourite shows at once and stays when the server agrees`() = runBlocking {
        await { it.loadedOnce && it.items.isNotEmpty() }
        viewModel.onToggle("99", Toggle.Favourite)
        assertTrue(await { it.row("99").state.favourited }.row("99").state.favourited)
        assertFalse(viewModel.uiState.value.actionFailed)
    }

    @Test
    fun `a favourite the server refuses is taken back and reported`() = runBlocking {
        await { it.loadedOnce && it.items.isNotEmpty() }
        timeline.failActions = true
        viewModel.onToggle("99", Toggle.Favourite)
        // the guess is shown first, then taken back when the server refuses
        val state = await { it.actionFailed && !it.row("99").state.favourited }
        assertFalse(state.row("99").state.favourited)
        viewModel.onActionFailureShown()
        assertFalse(await { !it.actionFailed }.actionFailed)
    }

    @Test
    fun `the source chosen is kept for the account and loads its own timeline`() = runBlocking {
        await { it.loadedOnce && it.items.isNotEmpty() }
        viewModel.onSource(TimelineSource.Local)
        val state = await { it.source == TimelineSource.Local && it.loadedOnce && it.items.isNotEmpty() }
        assertEquals("100", state.postIds().first())
        val account = accounts.activeAccount.value!!
        assertEquals(TimelineSource.Local, settings.settings(account.id).first().homeSource)
    }

    @Test
    fun `a server that disables its live feeds offers only Following, whatever was chosen before`() = runBlocking {
        await { it.loadedOnce && it.items.isNotEmpty() }
        val account = accounts.activeAccount.value!!
        settings.update(account.id) { it.copy(homeSource = TimelineSource.Local) }
        await { it.source == TimelineSource.Local }
        accounts.updateCapabilities(account.id, account.capabilities.copy(localFeed = false, federatedFeed = false))
        val state = await { it.sources == listOf(TimelineSource.Home) }
        assertEquals(TimelineSource.Home, state.source)
    }

    @Test
    fun `a hashtag reads its own public timeline, with no sources to choose and nothing hidden`() = runBlocking {
        val tag = create(TimelineFeed.Tag("surf"))
        val state = withTimeout(10.seconds) { tag.uiState.first { it.loadedOnce && it.items.isNotEmpty() } }
        assertEquals(TimelineSource.Hashtag("surf"), state.source)
        assertEquals(emptyList<TimelineSource>(), state.sources)
        // stored under the hashtag's own timeline, apart from home's
        val reader = accounts.activeAccount.value!!
        assertTrue(
            cache.timelineDao().entries(
                reader.id,
                TimelineKey.home(TimelineSource.Hashtag("surf")).storageKey,
            ).isNotEmpty(),
        )
    }

    @Test
    fun `a swipe does what the settings chose`() = runBlocking {
        preferences.setSwipeTowardsEnd(SwipeAction.Reply)
        preferences.setSwipeTowardsStart(SwipeAction.None)
        val state = await { it.swipeTowardsEnd == SwipeAction.Reply }
        assertEquals(SwipeAction.None, state.swipeTowardsStart)
    }
}
