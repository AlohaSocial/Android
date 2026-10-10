// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.testing.SignedInFixture

/** Statistics as Social 0.26.181 answers them, trimmed; a fresh count is refused when [refuseFresh]. */
private class Counts(private val refuseFresh: Boolean, private val refuseYear: Boolean = false) : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val days = request.url.queryParameter("days")
        val fresh = request.url.queryParameter("fresh")
        asked += "days=$days fresh=${fresh ?: "false"}"
        val refusedFresh = refuseFresh && fresh == "true"
        val refusedYear = refuseYear && days == "365"
        if (refusedFresh || refusedYear) {
            return MockResponse.Builder().code(500).body("{}").build()
        }
        val body = """{"account":{"acct":"alice","followers":2,"following":2},
            "posts":{"total":19},"engagement":{"likes":2,"boosts":1,"replies":1},
            "window":{"days":$days,"counted":19,"capped":false}}"""
        return MockResponse.Builder().body(body).addHeader("content-type", "application/json").build()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StatisticsViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val store = ViewModelStore()
    private val server = MockWebServer()

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    private fun open(counts: Counts): StatisticsViewModel = runBlocking {
        server.dispatcher = counts
        server.start()
        val reader = fixture.signIn(server.url("/"))
        fixture.connectNextcloud(reader.id, "Basic YWxpY2U6YXBw")
        StatisticsViewModel(reader.id, context, fixture.accounts, NextcloudExtras(fixture.clients))
            .also { store.put("stats", it) }
    }

    @Test
    fun `ninety days first, another window when picked, and counting again asks for a fresh count`() = runBlocking {
        val counts = Counts(refuseFresh = false)
        val viewModel = open(counts)
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        viewModel.onWindow(365)
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.statistics?.window?.days == 365 && !it.refreshing } }
        viewModel.onCountAgain()
        withTimeout(WAIT_MILLIS) { while (counts.asked.size < 3) kotlinx.coroutines.delay(POLL_MILLIS) }
        assertEquals(listOf("days=90 fresh=false", "days=365 fresh=false", "days=365 fresh=true"), counts.asked)
    }

    @Test
    fun `a count again that fails keeps the numbers shown and says so`() = runBlocking {
        val viewModel = open(Counts(refuseFresh = true))
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        viewModel.onCountAgain()
        val after = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.failed } }
        assertEquals(PageStatus.Ready, after.status)
        assertEquals(19.0, after.statistics?.posts?.get("total"))
    }

    @Test
    fun `a window that fails to count goes back to the one the numbers shown belong to`() = runBlocking {
        val viewModel = open(Counts(refuseFresh = false, refuseYear = true))
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        viewModel.onWindow(365)
        val after = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.failed } }
        assertEquals(90, after.days)
        assertEquals(90, after.statistics?.window?.days)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
