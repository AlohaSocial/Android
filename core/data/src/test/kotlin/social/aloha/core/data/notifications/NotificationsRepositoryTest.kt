// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.SignedInFixture

/** Answers notifications grouped and flat, markers, and requests; remembers every request with its body. */
private class Server : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()
    var marker = "100"
    var bulk = true

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} ${request.url.encodedPath}?${request.url.encodedQuery.orEmpty()} " +
            request.body?.utf8().orEmpty()
        return when {
            path.endsWith("/api/v2/notifications") -> json(GROUPED)

            path.endsWith("/api/v1/notifications") -> json(FLAT)

            path.endsWith("/markers") && request.method == "GET" ->
                json("""{"notifications":{"last_read_id":"$marker","version":1}}""")

            path.endsWith("/markers") -> json("{}")

            path.endsWith("/requests/accept") || path.endsWith("/requests/dismiss") ->
                if (bulk) json("{}") else json("""{"error":"Not found"}""", 404)

            path.contains("/requests/") -> json("{}")

            else -> json("{}", 404)
        }
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()

    companion object {
        private const val ALICE = """{"id":"1","username":"alice","acct":"alice","display_name":"Alice"}"""
        private const val BOB = """{"id":"2","username":"bob","acct":"bob@else.where","display_name":"Bob"}"""
        private const val POST =
            """{"id":"s1","content":"<p>Hello</p>","account":$ALICE,"created_at":"2026-09-30T10:00:00Z"}"""

        val GROUPED = """{"accounts":[$ALICE,$BOB],"statuses":[$POST],"notification_groups":[""" +
            """{"group_key":"favourite-s1","notifications_count":3,"type":"favourite",""" +
            """"most_recent_notification_id":"120","page_min_id":"110","sample_account_ids":["2","1"],""" +
            """"status_id":"s1","latest_page_notification_at":"2026-09-30T11:00:00Z"},""" +
            """{"group_key":"ungrouped-90","notifications_count":1,"type":"mention",""" +
            """"most_recent_notification_id":"90","sample_account_ids":["1"],"status_id":"s1"},""" +
            """{"group_key":"ungrouped-80","notifications_count":1,"type":"quote_update",""" +
            """"most_recent_notification_id":"80","sample_account_ids":["1"]}]}"""

        val FLAT = """[{"id":"95","type":"follow","account":$BOB,"created_at":"2026-09-30T09:00:00Z"}]"""
    }
}

@RunWith(RobolectricTestRunner::class)
class NotificationsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val answers = Server()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val unread = UnreadCounts()
    private val repository = NotificationsRepository(fixture.clients, unread)
    private val filtering = NotificationFiltering(fixture.clients)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private suspend fun signIn(grouped: Boolean) = fixture.signIn(
        server.url("/"),
        ServerCapabilities.minimal(server.url("/").toString()).copy(groupedNotifications = grouped),
    )

    @Test
    fun `groups keep their sample accounts and post, and a kind this app does not know is left out`() = runBlocking {
        val page = (repository.page(signIn(grouped = true), emptySet()) as Answer.Got).value
        assertEquals(listOf("favourite-s1", "ungrouped-90"), page.items.map { it.key })
        val favourites = page.items.first()
        assertEquals(listOf("Bob", "Alice"), favourites.accounts.map { it.displayName })
        assertEquals(3, favourites.count)
        assertEquals("s1", favourites.status?.id)
        assertEquals("favourite-s1", favourites.groupKey)
        // one mention is no group to list
        assertNull(page.items[1].groupKey)
        // a short page is the last one
        assertNull(page.olderThan)
    }

    @Test
    fun `a server without grouping sends one row per event, filtered by the kinds asked for`() = runBlocking {
        val page = (repository.page(signIn(grouped = false), setOf(NotificationKind.Follow)) as Answer.Got).value
        assertEquals(listOf(NotificationKind.Follow), page.items.map { it.kind })
        assertEquals("95", page.items.single().newestId)
        assertTrue(answers.asked.single().contains("types%5B%5D=follow"))
    }

    @Test
    fun `the read marker only moves forwards, and reading clears the badge at once`() = runBlocking {
        val account = signIn(grouped = true)
        unread.set(account.id, 7)
        assertEquals(Answer.Got("100"), repository.readMarker(account))
        repository.markRead(account, "90")
        assertEquals(0, unread.of(account.id))
        assertFalse(answers.asked.any { it.startsWith("POST") })
        repository.markRead(account, "120")
        assertTrue(answers.asked.last().startsWith("POST /api/v1/markers"))
        assertTrue(answers.asked.last().contains("notifications%5Blast_read_id%5D=120"))
        // another device read further meanwhile: this one never writes back below it
        answers.marker = "1000"
        repository.readMarker(account)
        repository.markRead(account, "130")
        assertEquals(1, answers.asked.count { it.startsWith("POST") })
    }

    @Test
    fun `requests are decided all at once, or one by one where the server has no bulk route`() = runBlocking {
        val account = signIn(grouped = true)
        assertNull(filtering.accept(account, listOf("r1", "r2")))
        assertEquals(1, answers.asked.count { it.startsWith("POST") })
        answers.bulk = false
        assertNull(filtering.dismiss(account, listOf("r1", "r2")))
        assertTrue(answers.asked.any { it.startsWith("POST /api/v1/notifications/requests/r1/dismiss") })
        assertTrue(answers.asked.any { it.startsWith("POST /api/v1/notifications/requests/r2/dismiss") })
    }

    @Test
    fun `ids compare as numbers, longer ones being newer`() {
        assertTrue(NotificationItem.isNewer("100", "99"))
        assertTrue(NotificationItem.isNewer("110", "100"))
        assertFalse(NotificationItem.isNewer("100", "100"))
        assertFalse(NotificationItem.isNewer("99", "100"))
        assertTrue(NotificationItem.isNewer("1", null))
    }
}
