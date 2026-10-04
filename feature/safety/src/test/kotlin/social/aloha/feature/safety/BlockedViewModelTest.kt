// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.safety.Blocking
import social.aloha.core.testing.SignedInFixture

/** One blocked account, one muted, one blocked server; unmuting is refused. */
private class Lists : Dispatcher() {
    val sent = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (request.method != "GET") sent += "${request.method} $path ${request.body?.utf8().orEmpty()}".trim()
        return when {
            path == "/api/v1/blocks" -> json(200, """[{"id":"7","username":"troll","acct":"troll@spam.example"}]""")
            path == "/api/v1/mutes" -> json(200, """[{"id":"8","username":"loud","acct":"loud"}]""")
            path == "/api/v1/domain_blocks" && request.method == "GET" -> json(200, """["spam.example"]""")
            path.endsWith("/unmute") -> json(500, "{}")
            path.endsWith("/unblock") -> json(200, """{"id":"7"}""")
            else -> json(200, "{}")
        }
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BlockedViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = Lists()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val store = ViewModelStore()

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    private fun open(): BlockedViewModel = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        BlockedViewModel(reader.id, fixture.accounts, Blocking(fixture.clients)).also { store.put("blocked", it) }
    }

    private suspend fun BlockedViewModel.await(done: (BlockedState) -> Boolean) =
        withTimeout(WAIT_MILLIS) { state.first(done) }

    @Test
    fun `an unblock goes at once and stays gone, a refused unmute comes back and says so`() = runBlocking {
        val viewModel = open()
        val loaded = viewModel.await { it.servers != null }
        assertEquals(listOf("@troll@spam.example"), loaded.blocked?.map { it.handle })
        assertEquals(listOf("spam.example"), loaded.servers)
        viewModel.unblock("7")
        assertEquals(emptyList<Kept>(), viewModel.state.value.blocked)
        viewModel.unmute("8")
        val refused = viewModel.await { it.refused }
        assertEquals(listOf("8"), refused.muted?.map { it.id })
        assertEquals(emptyList<Kept>(), refused.blocked)
        // both go out at once: the refusal can come back before the unblock reached the server
        withTimeout(WAIT_MILLIS) {
            while ("POST /api/v1/accounts/7/unblock" !in answers.sent) kotlinx.coroutines.delay(POLL_MILLIS)
        }
    }

    @Test
    fun `a refused unmute comes back alone, an unblock taken meanwhile stays taken`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.servers != null }
        viewModel.unmute("8")
        viewModel.unblock("7")
        viewModel.await { it.muted?.map { kept -> kept.id } == listOf("8") }
        assertEquals(emptyList<Kept>(), viewModel.state.value.blocked)
    }

    @Test
    fun `a server is blocked by its address, however it was typed`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.servers != null }
        assertTrue(viewModel.blockServer(" https://Bad.Example/@someone "))
        assertTrue(viewModel.blockServer("@loud@worse.example"))
        assertFalse(viewModel.blockServer("not a server"))
        assertEquals(listOf("bad.example", "spam.example", "worse.example"), viewModel.state.value.servers)
        withTimeout(WAIT_MILLIS) { while (answers.sent.size < 2) kotlinx.coroutines.delay(POLL_MILLIS) }
        assertEquals(
            listOf("POST /api/v1/domain_blocks domain=bad.example", "POST /api/v1/domain_blocks domain=worse.example"),
            answers.sent.sorted(),
        )
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
