// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.timeline.AccountRows
import social.aloha.core.data.timeline.MergedPage
import social.aloha.core.data.timeline.MergedPager
import social.aloha.core.data.timeline.MergedPost
import social.aloha.core.data.timeline.MergedTimeline
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.network.RateLimiter

@RunWith(RobolectricTestRunner::class)
class MergedPagerTest {
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
    private val servers = mutableListOf<AutoCloseable>()

    // a's post n is the post at minute n; b follows the same people a minute-for-minute ten posts behind,
    // so b's post n is a's post n - 10
    private val a = NumberedTimeline().apply {
        newest = 100
        fields = { stamped(it) }
    }
    private val b = NumberedTimeline().apply {
        newest = 100
        fields = { stamped(it - 10) }
    }

    @After
    fun close() {
        scope.cancel()
        accountsDb.close()
        cache.close()
        servers.forEach { it.close() }
    }

    private fun stamped(post: Int) = mapOf(
        "uri" to JsonPrimitive("https://other.social/$post"),
        "created_at" to JsonPrimitive(NOON.plusSeconds(post * 60L).toString()),
    )

    private suspend fun signedIn(timeline: Dispatcher, remoteId: String): SignedInAccount {
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val base = server.url("/")
        val capabilities = ServerCapabilities.minimal(base.toString())
        return accounts.signedIn(
            NewAccount(
                base.host,
                remoteId,
                "alice$remoteId",
                "Alice",
                null,
                null,
                capabilities,
                profilePending = false,
            ),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
    }

    private class Reader(
        val pager: MergedPager,
        val shown: List<Pair<SignedInAccount, TimelineKey>>,
        val rows: StateFlow<Map<String, List<TimelineRow>>>,
    ) {
        fun page(): MergedPage = MergedTimeline.merge(
            shown.map { (account, _) ->
                AccountRows.of(account.id, rows.value[account.id].orEmpty(), account.id in pager.states.value.ended)
            },
        )

        /** Waits until the stored rows of every account satisfy [until]. */
        suspend fun rowsWhere(until: (Map<String, List<TimelineRow>>) -> Boolean) {
            withTimeout(TIMEOUT) { rows.first(until) }
        }
    }

    private suspend fun reader(): Reader {
        val shown = listOf(signedIn(a, "6"), signedIn(b, "7")).map { it to TimelineKey.home() }
        val pager = MergedPager(timelines, Clock.systemUTC(), scope) { shown }
        val rows = pager.observe(shown).stateIn(scope, SharingStarted.Eagerly, emptyMap())
        pager.start(shown)
        return Reader(pager, shown, rows).apply { rowsWhere { it.size == 2 && it.values.all { r -> r.size == 20 } } }
    }

    private fun MergedPage.ids() = posts.map { it.number() }

    @Test
    fun `both accounts load at once, a post both see shows once, and the list stops where one is behind`() =
        runBlocking {
            val reader = reader()
            val (first, second) = reader.shown.map { it.first.id }
            val page = reader.page()
            // a reaches minute 81, b minute 71: below 81, a may still have posts it has not loaded
            assertEquals((100 downTo 81).toList(), page.ids())
            assertEquals(listOf(first, second), page.posts.single { it.number() == 85 }.seenBy)
            assertEquals(listOf(first), page.behind)

            reader.pager.older(page.behind)
            reader.rowsWhere { it[first]?.size == 40 }
            val further = reader.page()
            assertEquals((100 downTo 71).toList(), further.ids())
            assertEquals(listOf(second), further.behind)
        }

    @Test
    fun `a refresh over rows being read holds each account's new posts, and a gap is filled before older pages`() =
        runBlocking {
            val reader = reader()
            val (first, second) = reader.shown.map { it.first.id }
            a.newest = 105
            b.newest = 150
            reader.pager.refresh()
            val held = withTimeout(TIMEOUT) { reader.pager.states.first { it.held.size == 2 } }.held
            assertEquals((105 downTo 101).map(Int::toString).toSet(), held[first])
            assertEquals((150 downTo 131).map(Int::toString).toSet(), held[second])

            // b's twenty newest stand above a hole: b is behind until it is filled from above
            reader.rowsWhere { rows -> rows[second].orEmpty().any { it is TimelineRow.Gap } }
            assertEquals(listOf(second), reader.page().behind)
            reader.pager.older(listOf(second))
            reader.rowsWhere { rows -> rows[second].orEmpty().takeWhile { it !is TimelineRow.Gap }.size == 40 }
            assertTrue(reader.page().ids().containsAll((121 downTo 101).toList()))
        }

    @Test
    fun `what the second read of a page still filling brings waits behind the pill`() = runBlocking {
        val reads = AtomicInteger()
        val filling = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val answer = a.dispatch(request)
                if (reads.incrementAndGet() > 1) a.newest = 103
                return answer.newBuilder().addHeader("X-Social-Filling", "1").build()
            }
        }
        val shown = listOf(signedIn(filling, "6") to TimelineKey.home())
        val (account, key) = shown.single()
        timelines.refresh(account, key, RefreshPlan.of(hasFetchedBefore = false, newestRowId = null))
        val pager = MergedPager(timelines, Clock.systemUTC(), scope) { shown }
        pager.observe(shown).first { it.values.single().isNotEmpty() }
        pager.refresh()
        val held = withTimeout(TIMEOUT) { pager.states.first { !it.held[account.id].isNullOrEmpty() } }.held
        assertEquals((103 downTo 101).map(Int::toString).toSet(), held[account.id])
    }

    private fun MergedPost.number() = status.displayed.uri.substringAfterLast('/').toInt()

    private companion object {
        val NOON: Instant = Instant.parse("2026-10-11T12:00:00Z")
        const val TIMEOUT = 10_000L
    }
}
