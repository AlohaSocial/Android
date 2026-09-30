// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.data.timeline.PageOutcome
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.FeedMode
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.RateLimiter

@RunWith(RobolectricTestRunner::class)
class TimelineRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountsDb = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val cache = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val accounts = AccountRepository(
        accountsDb.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        Clock.systemUTC(),
        scope,
    )
    private val http = OkHttpClient()
    private val statuses = StatusRepository(cache.statusDao(), Clock.systemUTC())
    private val timelines = TimelineRepository(
        cache.timelineDao(),
        statuses,
        ClientFactory(http, RateLimiter(nowMillis = Clock.systemUTC()::millis), Dispatchers.IO, accounts),
        accounts,
        Clock.systemUTC(),
        Dispatchers.IO,
    )
    private val home = TimelineKey.home()
    private val servers = mutableListOf<AutoCloseable>()

    @After
    fun close() {
        scope.cancel()
        accountsDb.close()
        cache.close()
        servers.forEach { it.close() }
    }

    private suspend fun signedInAt(
        apiBase: HttpUrl,
        capabilities: ServerCapabilities = ServerCapabilities.minimal(apiBase.toString()),
    ): SignedInAccount = accounts.signedIn(
        NewAccount(apiBase.host, "6", "alice", "Alice", null, null, capabilities, profilePending = false),
        AccessToken(MockCredentials.ACCESS_TOKEN, ""),
    )

    private suspend fun rows(account: SignedInAccount, key: TimelineKey = home) =
        timelines.observe(account, key).first()

    private fun List<TimelineRow>.ids() = map { it.id }

    @Test
    fun `every mock configuration reads, refreshes and pages home, local and federated`() = runBlocking {
        for (configuration in MockServerConfiguration.entries) {
            val mock = MockSocialServer(configuration).start().also { servers += it }
            val account = signedInAt(mock.apiBase)
            for (source in listOf(TimelineSource.Home, TimelineSource.Local, TimelineSource.Federated)) {
                val key = TimelineKey(FeedMode.Home, source)
                val first = timelines.refresh(
                    account,
                    key,
                    RefreshPlan.of(hasFetchedBefore = false, newestRowId = null),
                )
                assertTrue("$configuration $source: $first", first is PageOutcome.Loaded)
                val loaded = rows(account, key)
                if (configuration !=
                    MockServerConfiguration.Mastodon
                ) {
                    assertTrue("$configuration $source is empty", loaded.isNotEmpty())
                }
                val newest = loaded.filterIsInstance<TimelineRow.Post>().firstOrNull()?.id
                val again = timelines.refresh(
                    account,
                    key,
                    RefreshPlan.of(hasFetchedBefore = true, newestRowId = newest),
                )
                assertTrue("$configuration $source refresh: $again", again is PageOutcome.Loaded)
                val oldest = rows(account, key).filterIsInstance<TimelineRow.Post>().lastOrNull()?.id ?: continue
                val older = timelines.older(account, key, (first as PageOutcome.Loaded).nextCursor, oldest)
                assertTrue("$configuration $source older: $older", older is PageOutcome.Loaded)
            }
        }
    }

    @Test
    fun `a busy hour opens a gap under the newest posts, and filling it closes it`() = runBlocking {
        val timeline = NumberedTimeline().apply { newest = 100 }
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val account = signedInAt(server.url("/"))

        timelines.refresh(account, home, RefreshPlan.of(false, null))
        assertEquals((100 downTo 81).map(Int::toString), rows(account).ids())

        // fifty new posts: the refresh shows the newest twenty, then the hole the server has not sent yet
        timeline.newest = 150
        val refreshed = timelines.refresh(account, home, RefreshPlan.of(true, "100")) as PageOutcome.Loaded
        assertEquals((150 downTo 131).map(Int::toString), refreshed.arrived)
        val withGap = rows(account)
        assertEquals(
            (150 downTo 131).map(Int::toString) + "gap:131" + (100 downTo 81).map(Int::toString),
            withGap.ids(),
        )

        // a full page moves the gap down; the short one after it closes it
        timelines.fillGap(account, home, "gap:131", aboveId = "131")
        assertEquals(1, rows(account).count { it is TimelineRow.Gap })
        val gap = rows(account).single { it is TimelineRow.Gap }.id
        val above = rows(account).ids().let { it[it.indexOf(gap) - 1] }
        timelines.fillGap(account, home, gap, aboveId = above)
        assertEquals((150 downTo 81).map(Int::toString), rows(account).ids())
    }

    @Test
    fun `paging older follows the server's cursor and stops at the end`() = runBlocking {
        val timeline = NumberedTimeline().apply { newest = 30 }
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val account = signedInAt(server.url("/"))
        val first = timelines.refresh(account, home, RefreshPlan.of(false, null)) as PageOutcome.Loaded
        val second = timelines.older(account, home, first.nextCursor, oldestId = "11") as PageOutcome.Loaded
        assertEquals((30 downTo 1).map(Int::toString), rows(account).ids())
        assertTrue(second.reachedEnd)
    }

    @Test
    fun `a status updated once is updated in every timeline showing it`() = runBlocking {
        val timeline = NumberedTimeline().apply { newest = 5 }
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val account = signedInAt(server.url("/"))
        val local = TimelineKey(FeedMode.Home, TimelineSource.Local)
        timelines.refresh(account, home, RefreshPlan.of(false, null))
        timelines.refresh(account, local, RefreshPlan.of(false, null))
        val status = (rows(account).first() as TimelineRow.Post).status
        statuses.save(account.id, status.copy(favourited = true, favouritesCount = status.favouritesCount + 1))
        for (key in listOf(
            home,
            local,
        )) {
            assertTrue(((rows(account, key).first()) as TimelineRow.Post).status.favourited)
        }
    }

    @Test
    fun `a second refresh of the same timeline while one is running does not fetch twice`() = runBlocking {
        val timeline = NumberedTimeline().apply { newest = 5 }
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val account = signedInAt(server.url("/"))
        val outcomes = List(5) {
            async(Dispatchers.IO) { timelines.refresh(account, home, RefreshPlan.of(false, null)) }
        }.map { it.await() }
        assertTrue(outcomes.any { it is PageOutcome.Loaded })
        assertEquals(
            outcomes.size,
            outcomes.count { it is PageOutcome.Loaded } + outcomes.count { it == PageOutcome.Busy },
        )
        assertEquals(server.requestCount, outcomes.count { it is PageOutcome.Loaded })
    }

    @Test
    fun `filters come from the server, are kept, and a failed refresh keeps them`() = runBlocking {
        val mock = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start().also { servers += it }
        val account = signedInAt(mock.apiBase)
        val filters =
            FilterRepository(
                cache.filterDao(),
                ClientFactory(http, RateLimiter(nowMillis = Clock.systemUTC()::millis), Dispatchers.IO, accounts),
            )
        assertNull(filters.refresh(account))
        val stored = filters.observe(account.id).first()
        assertEquals(listOf("No spoilers"), stored.map { it.title })
        assertEquals(listOf("butler"), stored.single().keywords.map { it.keyword })

        mock.close()
        assertNotNull(filters.refresh(account))
        assertEquals(stored, filters.observe(account.id).first())
    }

    @Test
    fun `a media mode the server cannot narrow is filtered on the device`() = runBlocking {
        val mock = MockSocialServer(MockServerConfiguration.Mastodon).start().also { servers += it }
        val nextcloud = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start().also { servers += it }
        val account = signedInAt(nextcloud.apiBase)
        val photos = TimelineKey(FeedMode.Photos, TimelineSource.Local)
        timelines.refresh(account, photos, RefreshPlan.of(false, null))
        val posts = rows(account, photos).filterIsInstance<TimelineRow.Post>()
        assertTrue(posts.isNotEmpty())
        assertTrue(posts.all { it.status.displayed.mediaAttachments.isNotEmpty() })
        mock.close()
    }
}
