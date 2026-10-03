// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.moderation

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.OAuthCallbackInbox
import social.aloha.core.data.moderation.Moderation
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.network.endpoints.AdminAccountEndpoints.Origin
import social.aloha.core.network.endpoints.ModerationEndpoints.TrendKind
import social.aloha.core.testing.SignedInFixture

/**
 * Mastodon's admin API for one person whose role carries [permissions]. Until a token with the admin
 * scopes is issued (`admin-token`), every admin route answers 403 as Mastodon does for a missing scope.
 */
private class Admin(
    private val permissions: String?,
    private val scoped: Boolean = true,
    private val reports: String = REPORTS,
    private val role: Int = 200,
) : Dispatcher() {
    val sent = CopyOnWriteArrayList<String>()
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val body = request.body?.utf8().orEmpty()
        if (request.method != "GET") sent += "${request.method} $path $body".trim() else asked += request.url.toString()
        val token = request.headers["Authorization"].orEmpty()
        val admin = path.startsWith("/api/v1/admin/")
        return when {
            admin && !scoped && token != "Bearer admin-token" ->
                json(403, """{"error":"This action is outside the authorized scopes"}""")

            path == "/api/v1/accounts/verify_credentials" -> json(role, credentials())

            path == "/api/v1/admin/reports" -> json(200, reports)

            path == "/api/v1/admin/accounts" -> json(200, """[{"id":"9","username":"spam","domain":"bad.example"}]""")

            path == "/api/v1/admin/trends/tags" -> json(200, """[{"id":"37","name":"aloha"}]""")

            path == "/api/v1/admin/trends/statuses" -> json(200, "[]")

            path == "/api/v1/admin/trends/links" -> json(200, """[{"id":"12","url":"https://news.example/a"}]""")

            path.endsWith("/resolve") -> json(500, "{}")

            path == "/api/v1/apps" -> json(200, """{"client_id":"moderator","client_secret":"s","scopes":[]}""")

            path == "/oauth/token" -> json(200, """{"access_token":"admin-token","scope":"read admin:read"}""")

            path == "/api/v2/instance" -> json(200, """{"domain":"mastodon.example","title":"Example"}""")

            admin || path.startsWith("/api/") -> json(200, "{}")

            else -> json(404, "{}")
        }
    }

    private fun credentials(): String {
        val role = permissions?.let { """{"id":"3","name":"Admin","permissions":"$it"}""" } ?: "null"
        return """{"id":"6","username":"alice","acct":"alice","role":$role}"""
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()

    companion object {
        const val REPORTS =
            """[{"id":"1","category":"spam","comment":"ads everywhere","account":{"id":"2","username":"bob",""" +
                """"acct":"bob"},"target_account":{"id":"9","username":"spam","acct":"spam@bad.example"},""" +
                """"statuses":[]}]"""
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ModerationViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer()
    private val inbox = OAuthCallbackInbox()
    private val store = ViewModelStore()

    @After
    fun close() {
        Dispatchers.resetMain()
        store.clear()
        fixture.close()
        server.close()
    }

    private fun open(answers: Admin): ModerationViewModel = runBlocking {
        server.dispatcher = answers
        server.start()
        val reader = fixture.signIn(server.url("/"))
        ModerationViewModel(reader.id, fixture.accounts, Moderation(fixture.clients), fixture.coordinator, inbox)
            .also { store.put("moderation", it) }
    }

    private suspend fun ModerationViewModel.await(done: (ModerationState) -> Boolean) =
        withTimeout(WAIT_MILLIS) { state.first(done) }

    @Test
    fun `an administrator sees every part, a server without a role shows none and asks nothing`() = runBlocking {
        val loaded = open(Admin("1")).await { it.reports != null && it.accounts != null && it.posts != null }
        assertEquals(ModerationTab.entries, loaded.tabs)
        assertEquals("spam@bad.example", loaded.reports?.single()?.targetAccount?.acct)
        assertEquals("37", loaded.tags?.single()?.id)
        server.close()

        val nextcloud = MockWebServer()
        val answers = Admin(null)
        nextcloud.dispatcher = answers
        nextcloud.start()
        val reader = fixture.signIn(nextcloud.url("/"))
        val none = ModerationViewModel(
            reader.id,
            fixture.accounts,
            Moderation(fixture.clients),
            fixture.coordinator,
            inbox,
        ).also { store.put("none", it) }.await { it.role != null }
        assertTrue(none.tabs.isEmpty())
        assertTrue(nextcloud.takeRequest().url.encodedPath.endsWith("verify_credentials"))
        assertEquals(1, nextcloud.requestCount)
        nextcloud.close()
    }

    @Test
    fun `a report moderator gets only reports, acting on one sends the report along, a refusal comes back`() =
        runBlocking {
            val answers = Admin("16")
            val viewModel = open(answers)
            val loaded = viewModel.await { it.reports != null }
            assertEquals(listOf(ModerationTab.Reports), loaded.tabs)
            assertNull(loaded.accounts)
            viewModel.actOnReport(loaded.reports!!.single(), AdminAccountAction.Silence)
            withTimeout(WAIT_MILLIS) { while (answers.sent.isEmpty()) kotlinx.coroutines.delay(POLL_MILLIS) }
            assertEquals("POST /api/v1/admin/accounts/9/action type=silence&report_id=1", answers.sent.single())
            assertEquals(emptyList<Any>(), viewModel.state.value.reports)

            viewModel.showResolved(false)
            viewModel.await { it.reports?.size == 1 }
            viewModel.resolve("1", resolved = true)
            val refused = viewModel.await { it.refused }
            assertEquals(listOf("1"), refused.reports?.map { it.id })
        }

    @Test
    fun `a refused decision brings back only its own report, the one taken meanwhile stays gone`() = runBlocking {
        val two = Admin.REPORTS.removeSuffix("]") +
            """,{"id":"2","category":"spam","target_account":{"id":"8","username":"ads","acct":"ads"}}]"""
        val viewModel = open(Admin("16", reports = two))
        val loaded = viewModel.await { it.reports?.size == 2 }
        // resolving answers 500 here, acting answers 200
        viewModel.resolve("1", resolved = true)
        viewModel.actOnReport(loaded.reports!!.last(), AdminAccountAction.Silence)
        // the refused one back in its place, the other still gone
        viewModel.await { state -> state.reports?.map { it.id } == listOf("1") }
        Unit
    }

    @Test
    fun `a role that could not be asked for is a failure to retry, not an empty console`() = runBlocking {
        val failed = open(Admin("1", role = 503)).await { it.failed }
        assertNull(failed.role)
        assertTrue(failed.tabs.isEmpty())
    }

    @Test
    fun `accounts are found by origin and username, and a link is hidden by its id`() = runBlocking {
        val answers = Admin("1")
        val viewModel = open(answers)
        val loaded = viewModel.await { it.accounts != null && it.links != null }
        assertEquals("12", loaded.links?.single()?.id)
        viewModel.findAccounts(origin = Origin.Local, username = "@spa ")
        viewModel.await { it.accounts != null && it.origin == Origin.Local }
        val search = answers.asked.last { "admin/accounts" in it }.toHttpUrl()
        assertEquals("local", search.queryParameter("origin"))
        assertEquals("spa", search.queryParameter("username"))
        assertEquals("active", search.queryParameter("status"))

        viewModel.review(TrendKind.Links, "12", approve = false)
        withTimeout(WAIT_MILLIS) { while (answers.sent.isEmpty()) kotlinx.coroutines.delay(POLL_MILLIS) }
        assertEquals("POST /api/v1/admin/trends/links/12/reject", answers.sent.single())
        assertEquals(emptyList<Any>(), viewModel.state.value.links)
    }

    @Test
    fun `without the admin scopes the console asks for them, and loads once the server granted them`() = runBlocking {
        val answers = Admin("1", scoped = false)
        val viewModel = open(answers)
        viewModel.await { it.consent }
        viewModel.allow()
        val url = viewModel.await { it.open != null }.open!!.toHttpUrl()
        assertEquals("read write follow push admin:read admin:write", url.queryParameter("scope"))
        assertTrue(answers.sent.any { it.startsWith("POST /api/v1/apps") && "admin%3Awrite" in it })
        viewModel.onOpened()

        inbox.deliver("alohasocial://oauth-callback?code=granted&state=${url.queryParameter("state")}")
        val granted = viewModel.await { !it.consent && it.reports != null }
        assertFalse(granted.authorizing)
        assertEquals("1", granted.reports?.single()?.id)
        assertNull(inbox.callback.value)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
