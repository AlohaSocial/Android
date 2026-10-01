// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.notifications.NotificationFiltering
import social.aloha.core.data.notifications.NotificationsRepository
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

private class Notifications : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path?${request.url.encodedQuery.orEmpty()} ${request.body?.utf8().orEmpty()}"
        return when {
            path.endsWith("/api/v2/notifications") -> json(PAGE)

            path.endsWith("/favourite-s1/accounts") -> json("[$ALICE,$BOB,$CAROL]")

            path.endsWith("/markers") && request.method == "GET" ->
                json("""{"notifications":{"last_read_id":"100","version":1}}""")

            path.endsWith("/api/v2/notifications/policy") ->
                json("""{"for_not_following":"accept","summary":{"pending_requests_count":4}}""")

            else -> json("{}")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val ALICE = """{"id":"1","username":"alice","acct":"alice","display_name":"Alice"}"""
        const val BOB = """{"id":"2","username":"bob","acct":"bob","display_name":"Bob"}"""
        const val CAROL = """{"id":"3","username":"carol","acct":"carol","display_name":"Carol"}"""
        const val POST = """{"id":"s1","content":"<p>Hello</p>","account":$ALICE}"""
        const val PAGE = """{"accounts":[$ALICE,$BOB],"statuses":[$POST],"notification_groups":[""" +
            """{"group_key":"favourite-s1","notifications_count":3,"type":"favourite",""" +
            """"most_recent_notification_id":"120","sample_account_ids":["2","1"],"status_id":"s1"},""" +
            """{"group_key":"ungrouped-90","notifications_count":1,"type":"mention",""" +
            """"most_recent_notification_id":"90","sample_account_ids":["1"],"status_id":"s1"}]}"""
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
            fixture.accounts,
            fixture.notifications,
            NotificationFiltering(fixture.clients),
            unread,
            SyncSettings(
                AccountSettingsStore(InMemoryDataStore(emptyMap())),
                AppPreferences(InMemoryDataStore(emptyPreferences())),
            ),
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
    fun `a group's others lists everyone in it`() = runBlocking {
        viewModel.onShown(isShown = true)
        await { it.loadedOnce }
        viewModel.onOthers("favourite-s1")
        val group = await { it.group?.accounts != null }.group!!
        assertEquals(listOf("Alice", "Bob", "Carol"), group.accounts!!.map { it.displayName })
        viewModel.onGroupClosed()
        assertEquals(null, await { it.group == null }.group)
    }

    @Test
    fun `a chip asks the server for that kind, and follows bring follow requests too`() = runBlocking {
        viewModel.onShown(isShown = true)
        await { it.loadedOnce }
        viewModel.onKind(NotificationKind.Follow)
        // the chip is set before its page is asked for, so wait for the request rather than the state
        val asked = withTimeout(10.seconds) {
            while (notifications.asked.none { "types%5B%5D=follow" in it }) delay(POLL_MS)
            notifications.asked.last { "types%5B%5D=follow" in it }
        }
        assertTrue(asked, asked.startsWith("GET /api/v2/notifications?") && "types%5B%5D=follow_request" in asked)
    }

    private companion object {
        const val POLL_MS = 10L
    }
}
