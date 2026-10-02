// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.year.YearInReview
import social.aloha.core.model.AnnualArchetype
import social.aloha.core.testing.SignedInFixture

/** 2025 and 2026, the newer with its most favourited post, as Nextcloud Social sends them; or nothing. */
private class Years(private val reports: Boolean) : Dispatcher() {
    val read = mutableListOf<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (path.endsWith("/read")) read += path
        return when {
            path.endsWith("/read") -> json(200, "{}")
            reports -> json(200, BODY)
            else -> json(404, """{"error":"Record not found"}""")
        }
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()

    companion object {
        val BODY = """
            {"annual_reports":[
              {"year":2025,"data":{"archetype":"lurker","time_series":[],"top_hashtags":[],"top_statuses":{}}},
              {"year":2026,"data":{"archetype":"oracle",
                "time_series":[{"month":9,"statuses":19,"followers":2}],
                "top_hashtags":[{"name":"aloha","count":3}],
                "top_statuses":{"by_favourites":"s1"}}}],
             "accounts":[],
             "statuses":[{"id":"s1","created_at":"2026-09-20T10:00:00Z","content":"<p>Surf's up</p>",
               "account":{"id":"6","username":"alice","acct":"alice"}}]}
        """.trimIndent()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class YearViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val store = ViewModelStore()

    @After
    fun close() {
        Dispatchers.resetMain()
        store.clear()
        fixture.close()
    }

    private fun open(server: MockWebServer): YearViewModel = runBlocking {
        fixture.signIn(server.url("/"))
        fixture.accounts.activeAccount.filterNotNull().first()
        YearViewModel(fixture.accounts, YearInReview(fixture.clients)).also { store.put("year", it) }
    }

    @Test
    fun `the newest year shows first, with its top post, and is marked read`() = runBlocking {
        val years = Years(reports = true)
        MockWebServer().use { server ->
            server.dispatcher = years
            server.start()
            val viewModel = open(server)
            val state = withTimeout(WAIT_MILLIS) { viewModel.state.first { !it.loading } }
            assertEquals(listOf(2026, 2025), state.reports.map { it.year })
            assertEquals(AnnualArchetype.Oracle, state.report?.data?.archetype)
            assertEquals(listOf(TopPost(TopKind.Favourites, "s1", "Surf's up")), state.posts[2026])
            withTimeout(WAIT_MILLIS) { while (years.read.isEmpty()) kotlinx.coroutines.delay(POLL_MILLIS) }
            assertEquals(listOf("/api/v1/annual_reports/2026/read"), years.read)
            viewModel.show(2025)
            assertEquals(2025, viewModel.state.value.report?.year)
        }
    }

    @Test
    fun `a server without years says so, which is not trouble`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = Years(reports = false)
            server.start()
            val state = withTimeout(WAIT_MILLIS) { open(server).state.first { !it.loading } }
            assertTrue(state.none)
            assertEquals(null, state.trouble)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
