// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.timeline.MergedPager
import social.aloha.core.data.timeline.PageOutcome
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.RateLimiter

/**
 * Nextcloud Social's `X-Social-Filling` and `X-Social-Profile-Filling`: a page the server is still filling
 * in is read once more, and an account's posts still being read from elsewhere are not yet their end.
 */
@RunWith(RobolectricTestRunner::class)
class TimelineFillingTest {
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
    private val timelines = TimelineRepository(
        cache.timelineDao(),
        StatusRepository(cache.statusDao(), Clock.systemUTC()),
        ClientFactory(OkHttpClient(), RateLimiter(nowMillis = Clock.systemUTC()::millis), Dispatchers.IO, accounts),
        accounts,
        Clock.systemUTC(),
        Dispatchers.IO,
    )
    private val home = TimelineKey.home()
    private val bobsPosts = TimelineKey.home(TimelineSource.Account("7", includeReplies = false, onlyMedia = false))
    private val servers = mutableListOf<AutoCloseable>()

    @After
    fun close() {
        scope.cancel()
        accountsDb.close()
        cache.close()
        servers.forEach { it.close() }
    }

    /** Signed in at [apiBase], a Nextcloud Social that pages an account's posts as it is asked to. */
    private suspend fun signedInAt(apiBase: HttpUrl): SignedInAccount = accounts.signedIn(
        NewAccount(
            apiBase.host,
            "6",
            "alice",
            "Alice",
            null,
            null,
            ServerCapabilities.minimal(apiBase.toString())
                .copy(softwareName = "nextcloud-social", softwareVersion = "0.26.278"),
            profilePending = false,
        ),
        AccessToken(MockCredentials.ACCESS_TOKEN, ""),
    )

    private suspend fun ids(account: SignedInAccount) = timelines.observe(account, home).first().map { it.id }

    private fun served(dispatcher: Dispatcher) = MockWebServer().apply {
        this.dispatcher = dispatcher
        start()
    }.also { servers += it }

    /** A page as Nextcloud Social answers it while it reads on in the background: no posts, no cursor. */
    private fun stillFilling(profile: Boolean) = MockResponse.Builder().code(200).body("[]")
        .addHeader("content-type", "application/json")
        .addHeader("X-Social-Filling", "1")
        .apply { if (profile) addHeader("X-Social-Profile-Filling", "1") }
        .build()

    /** A server answering the first [filling] requests with [stillFilling], then as [timeline] does. */
    private fun fillingFirst(timeline: NumberedTimeline, filling: Int, profile: Boolean): MockWebServer {
        val left = AtomicInteger(filling)
        return served(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    if (left.getAndDecrement() > 0) stillFilling(profile) else timeline.dispatch(request)
            },
        )
    }

    /**
     * A server answering the first pages of [timeline] as still filling, one for each of [then], the newest
     * post after it; a page further down waits [olderDelay] milliseconds for its answer.
     */
    private fun filling(timeline: NumberedTimeline, vararg then: Int, olderDelay: Long = 0): MockWebServer {
        val reads = AtomicInteger()
        return served(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val answer = timeline.dispatch(request).newBuilder()
                    if (request.url.queryParameter("max_id") != null) {
                        answer.headersDelay(olderDelay, TimeUnit.MILLISECONDS)
                    }
                    val newest = then.getOrNull(reads.getAndIncrement()) ?: return answer.build()
                    // the posts the server was reading arrive while the first answer is on its way
                    timeline.newest = newest
                    return answer.addHeader("X-Social-Filling", "1").build()
                }
            },
        )
    }

    /**
     * A hashtag's page as Nextcloud Social reads its posts from elsewhere: [first], still filling, then [later],
     * where a post read meanwhile sits by when it was published, its nid made from that time. Further down
     * there is [older].
     */
    private fun backFilling(first: List<Int>, later: List<Int>, older: List<Int> = emptyList()): MockWebServer {
        val reads = AtomicInteger()
        return served(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = when {
                    request.url.queryParameter("max_id") != null -> statuses(older, filling = false)
                    reads.getAndIncrement() == 0 -> statuses(first, filling = true)
                    else -> statuses(later, filling = false)
                }
            },
        )
    }

    private fun statuses(ids: List<Int>, filling: Boolean): MockResponse {
        val template = NumberedTimeline.homeTemplate()
        val body = JsonArray(ids.map { JsonObject(template + ("id" to JsonPrimitive("$it")) + ("reblog" to JsonNull)) })
        return MockResponse.Builder().code(200).body(body.toString())
            .addHeader("content-type", "application/json")
            .apply { if (filling) addHeader("X-Social-Filling", "1") }
            .build()
    }

    @Test
    fun `an account's page that ends while its server still reads older posts is read again`() = runBlocking {
        val server = fillingFirst(NumberedTimeline().apply { newest = 30 }, filling = 1, profile = true)
        val account = signedInAt(server.url("/"))
        val outcome = timelines.older(account, bobsPosts, cursor = null, oldestId = "11") as PageOutcome.Loaded
        assertEquals((10 downTo 1).map(Int::toString), outcome.arrived)
        assertEquals(2, server.requestCount)
        assertEquals(server.takeRequest().url, server.takeRequest().url)
    }

    @Test
    fun `an account's posts still being read after the reads allowed are not yet their end`() = runBlocking {
        val server = fillingFirst(NumberedTimeline(), filling = Int.MAX_VALUE, profile = true)
        val account = signedInAt(server.url("/"))
        val outcome = timelines.older(account, bobsPosts, cursor = null, oldestId = "11") as PageOutcome.Loaded
        assertFalse(outcome.reachedEnd)
        assertNull(outcome.nextCursor)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a page still filling only its counts ends where the server says, read once`() = runBlocking {
        val server = fillingFirst(NumberedTimeline(), filling = Int.MAX_VALUE, profile = false)
        val account = signedInAt(server.url("/"))
        val outcome = timelines.older(account, bobsPosts, cursor = null, oldestId = "11") as PageOutcome.Loaded
        assertTrue(outcome.reachedEnd)
        assertTrue(outcome.filling)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a refresh the server was still filling in is read once more, and only that one`() = runBlocking {
        val server = filling(NumberedTimeline().apply { newest = 20 }, 25)
        val account = signedInAt(server.url("/"))
        val plan = RefreshPlan.of(false, null)
        val first = timelines.refresh(account, home, plan) as PageOutcome.Loaded
        assertTrue(first.filling)
        val refilled = timelines.refill(account, home, plan, first)
        assertEquals((25 downTo 6).map(Int::toString), refilled?.arrived)
        assertEquals((25 downTo 6).map(Int::toString), ids(account))
        assertEquals(server.takeRequest().url, server.takeRequest().url)
        assertNull(timelines.refill(account, home, plan, refilled ?: first))
        assertNull(timelines.refill(account, TimelineKey.home(TimelineSource.Bookmarks), plan, first))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a cold page read again puts the posts read from elsewhere where they were published`() = runBlocking {
        val server = backFilling(first = listOf(30, 10), later = listOf(30, 20, 10))
        val account = signedInAt(server.url("/"))
        val plan = RefreshPlan.of(false, null)
        timelines.refill(account, home, plan, timelines.refresh(account, home, plan))
        assertEquals(listOf("30", "20", "10"), ids(account))
    }

    @Test
    fun `a cold page is not read again over rows paged in since`() = runBlocking {
        val server = backFilling(first = listOf(30, 10), later = listOf(30, 20, 10), older = listOf(5))
        val account = signedInAt(server.url("/"))
        val plan = RefreshPlan.of(false, null)
        val first = timelines.refresh(account, home, plan)
        timelines.older(account, home, cursor = null, oldestId = "10")
        assertNull(timelines.refill(account, home, plan, first))
        assertEquals(listOf("30", "10", "5"), ids(account))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a second read waits for a page further down on its way rather than being lost to it`() = runBlocking {
        val server = filling(NumberedTimeline().apply { newest = 20 }, 20, 23, olderDelay = 3_000)
        val account = signedInAt(server.url("/"))
        timelines.refresh(account, home, RefreshPlan.of(false, null))
        val plan = RefreshPlan.of(true, "20")
        val first = timelines.refresh(account, home, plan)
        val older = async { timelines.older(account, home, cursor = null, oldestId = "1") }
        assertEquals((23 downTo 21).map(Int::toString), timelines.refill(account, home, plan, first)?.arrived)
        assertTrue(older.await() is PageOutcome.Loaded)
    }

    @Test
    fun `what the second read of a filling timeline brings waits behind the pill, and nothing reads a third`() =
        runBlocking {
            val server = filling(NumberedTimeline().apply { newest = 20 }, 20, 23, 23)
            val account = signedInAt(server.url("/"))
            timelines.refresh(account, home, RefreshPlan.of(false, null))
            val pager = TimelinePager(timelines, Clock.systemUTC(), scope) { account to home }
            pager.observe(account, home).first { it.isNotEmpty() }
            pager.refresh()
            val held = withTimeout(FILLING_WAIT) { pager.states.first { it.held.isNotEmpty() }.held }
            assertEquals((23 downTo 21).map(Int::toString).toSet(), held)
            delay(FILLING_WAIT)
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `a timeline whose first read found nothing yet shows what the second brings, and pages on`() = runBlocking {
        val server = fillingFirst(NumberedTimeline().apply { newest = 20 }, filling = 1, profile = false)
        val account = signedInAt(server.url("/"))
        val pager = TimelinePager(timelines, Clock.systemUTC(), scope) { account to home }
        pager.start(account, home)
        val state = withTimeout(FILLING_WAIT) { pager.states.first { it.loadedOnce && !it.reachedEnd } }
        assertTrue(state.held.isEmpty())
        assertEquals((20 downTo 1).map(Int::toString), ids(account))
    }

    @Test
    fun `an account whose first read found nothing yet shows what the second brings in the merged timeline`() =
        runBlocking {
            val server = fillingFirst(NumberedTimeline().apply { newest = 20 }, filling = 1, profile = false)
            val account = signedInAt(server.url("/"))
            val pager = MergedPager(timelines, Clock.systemUTC(), scope) { listOf(account to home) }
            pager.start(listOf(account to home))
            val state = withTimeout(FILLING_WAIT) { pager.states.first { it.loadedOnce && it.ended.isEmpty() } }
            assertTrue(state.held.isEmpty())
            assertEquals((20 downTo 1).map(Int::toString), ids(account))
        }

    private companion object {
        /** Longer than [RefreshPlan.FILLING_DELAY] and the read after it. */
        const val FILLING_WAIT = 4_000L
    }
}
