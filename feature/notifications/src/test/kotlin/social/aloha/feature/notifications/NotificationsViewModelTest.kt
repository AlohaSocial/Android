// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AllAccounts
import social.aloha.core.data.notifications.NotificationFiltering
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.sync.UserStream
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AccountViewPreferences
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.NotificationPreferences
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

private class Notifications : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    /** Held until opened: the answer to a request for mentions and boosts together. */
    val mixed = CountDownLatch(1)

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val query = request.url.encodedQuery.orEmpty()
        asked += "${request.method} $path?$query ${request.body?.utf8().orEmpty()}"
        if ("types%5B%5D=mention" in query && "types%5B%5D=reblog" in query) {
            mixed.await(MIXED_SECONDS, TimeUnit.SECONDS)
        }
        return when {
            path.endsWith("/api/v2/notifications") -> json(PAGE)

            path.endsWith("/favourite-s1/accounts") -> json("[$ALICE,$BOB,$CAROL]")

            path.endsWith("/markers") && request.method == "GET" ->
                json("""{"notifications":{"last_read_id":"100","version":1}}""")

            path.endsWith("/statuses/s1/mute") -> json(POST.replace("\"account\"", "\"muted\":true,\"account\""))

            "/follow_requests/" in path -> json("""{"id":"${path.split('/').dropLast(1).last()}"}""")

            path.endsWith("/api/v2/notifications/policy") ->
                json("""{"for_not_following":"accept","summary":{"pending_requests_count":4}}""")

            else -> json("{}")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val MIXED_SECONDS = 10L
        const val ALICE = """{"id":"1","username":"alice","acct":"alice","display_name":"Alice"}"""
        const val BOB = """{"id":"2","username":"bob","acct":"bob","display_name":"Bob"}"""
        const val CAROL = """{"id":"3","username":"carol","acct":"carol","display_name":"Carol"}"""
        const val POST = """{"id":"s1","content":"<p>Hello</p>","account":$ALICE}"""
        const val PAGE = """{"accounts":[$ALICE,$BOB],"statuses":[$POST],"notification_groups":[""" +
            """{"group_key":"favourite-s1","notifications_count":3,"type":"favourite",""" +
            """"most_recent_notification_id":"120","sample_account_ids":["2","1"],"status_id":"s1"},""" +
            """{"group_key":"ungrouped-90","notifications_count":1,"type":"mention",""" +
            """"most_recent_notification_id":"90","sample_account_ids":["1"],"status_id":"s1"},""" +
            """{"group_key":"follow_request","notifications_count":2,"type":"follow_request",""" +
            """"most_recent_notification_id":"80","sample_account_ids":["2","1"]}]}"""
    }
}

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationsViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val notifications = Notifications()
    private val server = MockWebServer().apply {
        dispatcher = notifications
        start()
    }
    private val unread = fixture.unread
    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun create() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val capabilities = ServerCapabilities.minimal(server.url("/").toString())
            .copy(groupedNotifications = true, notificationPolicy = true)
        val account = fixture.signIn(server.url("/"), capabilities)
        unread.set(account.id, 2)
        viewModel = NotificationsViewModel(
            AllAccounts(fixture.accounts, AccountViewPreferences(InMemoryDataStore(emptyPreferences()))),
            fixture.notifications,
            NotificationFiltering(fixture.clients),
            unread,
            UserStream(fixture.accounts, { _, _, _ ->
                Closeable {}
            }, fixture.statuses, fixture.filters, TimelineSignals()),
            SyncSettings(
                AccountSettingsStore(InMemoryDataStore(emptyMap())),
                AppPreferences(InMemoryDataStore(emptyPreferences())),
                NotificationPreferences(InMemoryDataStore(emptyPreferences())),
            ),
            StatusInteractions(fixture.statuses, fixture.clients),
            AccountSettingsStore(InMemoryDataStore(emptyMap())),
            fixture.clock,
        )
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    private suspend fun await(predicate: (NotificationsUiState) -> Boolean): NotificationsUiState =
        withTimeout(10.seconds) { viewModel.uiState.first(predicate) }

    @Test
    fun `with a second account, both show together, each marked and read on its own server`() = runBlocking {
        val second = Notifications()
        val secondServer = MockWebServer().apply {
            dispatcher = second
            start()
        }
        try {
            val grouped = ServerCapabilities.minimal(secondServer.url("/").toString()).copy(groupedNotifications = true)
            val account = fixture.signIn(secondServer.url("/"), grouped, remoteId = "7")
            viewModel.onShown(isShown = true)
            val state = await { it.rows.size == 6 }
            assertTrue(state.rows.all { it.to != null })
            val host = secondServer.url("/").host
            // the second account's mention names its post by address and its author by handle, never by its ids
            val mention = state.rows.single { it.key == "${account.id}/ungrouped-90" }
            assertEquals(null, mention.statusId)
            assertEquals("remote:alice@$host", mention.accountId)
            withTimeout(10.seconds) {
                while (second.asked.none { it.startsWith("POST /api/v1/markers") }) delay(POLL_MS)
            }
            assertTrue(notifications.asked.any { it.startsWith("POST /api/v1/markers") })
        } finally {
            secondServer.close()
        }
    }

    @Test
    fun `what came after the read marker is new, and showing it marks it read`() = runBlocking {
        viewModel.onShown(isShown = true)
        val state = await { it.loadedOnce && it.pendingRequests == 4 }
        val (favourites, mention) = state.rows
        assertEquals(listOf(true, false), listOf(favourites.unread, mention.unread))
        assertEquals("Bob", favourites.name)
        assertEquals(2, favourites.others)
        assertEquals("Hello", favourites.preview)
        assertTrue(notifications.asked.any { it.startsWith("POST /api/v1/markers") && "last_read_id%5D=120" in it })
        assertEquals(0, unread.of(fixture.accounts.all().single().id))
    }

    @Test
    fun `muting from a mention mutes its conversation on the server, and says so once`() = runBlocking {
        viewModel.onShown(isShown = true)
        val state = await { it.rows.isNotEmpty() }
        viewModel.onMuteConversation(state.rows.single { it.kind == NotificationKind.Mention }.key)
        assertEquals(R.string.notifications_muted, await { it.notice != null }.notice)
        assertTrue(notifications.asked.any { it.startsWith("POST /api/v1/statuses/s1/mute") })
        viewModel.onNoticeShown()
        assertNull(await { it.notice == null }.notice)
    }

    @Test
    fun `a group's others lists everyone in it`() = runBlocking {
        viewModel.onShown(isShown = true)
        val row = await { state -> state.rows.any { it.groupKey == "favourite-s1" } }
            .rows.single { it.groupKey == "favourite-s1" }
        viewModel.onOthers(row.key)
        val group = await { it.group?.accounts != null }.group!!
        assertEquals(listOf("Alice", "Bob", "Carol"), group.accounts!!.map { it.displayName })
        viewModel.onGroupClosed()
        assertEquals(null, await { it.group == null }.group)
    }

    @Test
    fun `a chip asks the server for that kind, and follows bring requests and starter pack joins too`() = runBlocking {
        viewModel.onShown(isShown = true)
        await { it.loadedOnce }
        viewModel.onKind(NotificationKind.Follow)
        // the chip is set before its page is asked for, so wait for the request rather than the state
        val asked = withTimeout(10.seconds) {
            while (notifications.asked.none { "types%5B%5D=follow" in it }) delay(POLL_MS)
            notifications.asked.last { "types%5B%5D=follow" in it }
        }
        assertTrue(asked, asked.startsWith("GET /api/v2/notifications?") && "types%5B%5D=follow_request" in asked)
        assertTrue(asked, "types%5B%5D=bluesky%3Astarterpack_joined" in asked)
    }

    @Test
    fun `each chip's list is fetched ahead and shows at once, a mix not seen yet waits on its skeleton`() =
        runBlocking {
            viewModel.onShown(isShown = true)
            await { it.loadedOnce }
            // the last chip's kind is fetched last
            withTimeout(10.seconds) { while (notifications.asked.none { "types%5B%5D=update" in it }) delay(POLL_MS) }
            viewModel.onKind(NotificationKind.Mention)
            assertTrue(await { it.kinds == setOf(NotificationKind.Mention) }.loadedOnce)
            viewModel.onKind(NotificationKind.Reblog)
            val mix = setOf(NotificationKind.Mention, NotificationKind.Reblog)
            // its answer held back, the mix can only be on its skeleton
            assertFalse(await { it.kinds == mix }.loadedOnce)
            notifications.mixed.countDown()
            await { it.kinds == mix && it.loadedOnce }
            // the whole list, left for a chip, is back at once
            viewModel.onAllKinds()
            assertTrue(await { it.kinds.isEmpty() }.loadedOnce)
        }

    @Test
    fun `a follow request row that groups two people answers both`() = runBlocking {
        viewModel.onShown(isShown = true)
        val request = await { state -> state.rows.any { it.kind == NotificationKind.FollowRequest } }
            .rows.single { it.kind == NotificationKind.FollowRequest }
        viewModel.onFollowRequest(request.key, accept = true)
        await { state -> state.rows.none { it.kind == NotificationKind.FollowRequest } }
        val answered = notifications.asked.filter { "/follow_requests/" in it }
        assertTrue(answered.toString(), answered.any { "/follow_requests/1/authorize" in it })
        assertTrue(answered.toString(), answered.any { "/follow_requests/2/authorize" in it })
    }

    private companion object {
        const val POLL_MS = 10L
    }
}
