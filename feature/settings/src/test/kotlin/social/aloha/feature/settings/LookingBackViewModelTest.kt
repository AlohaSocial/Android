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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.WeeklyRecap
import social.aloha.core.testing.SignedInFixture

/** On this day as Social sends it: the reader's own posts, any order. */
private const val MEMORIES = """[
{"id":"1","created_at":"2024-10-10T09:00:00.000Z","content":"<p>Two years back</p>","visibility":"public",
 "account":{"id":"6","username":"alice","acct":"alice"}},
{"id":"2","created_at":"2025-10-10T09:00:00.000Z","content":"","visibility":"public",
 "media_attachments":[{"id":"m","type":"image","url":"https://x.test/m.png"}],
 "account":{"id":"6","username":"alice","acct":"alice"}}]"""

/** The recap off counts nothing; on, it answers with both weeks. */
private class Memories(private val refuse: Boolean) : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()
    private var enabled = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path"
        return when {
            path.endsWith("/on_this_day") -> json(200, MEMORIES)

            request.method == "POST" && refuse -> json(500, "{}")

            request.method == "POST" -> {
                enabled = request.body?.utf8()?.contains("enabled=1") == true
                json(200, """{"enabled":$enabled}""")
            }

            enabled -> json(200, """{"enabled":true,"this_week":3,"last_week":1}""")

            else -> json(200, """{"enabled":false,"this_week":0,"last_week":0}""")
        }
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LookingBackViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
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

    private fun open(memories: Memories): LookingBackViewModel = runBlocking {
        server.dispatcher = memories
        server.start()
        val reader = fixture.signIn(server.url("/"))
        fixture.connectNextcloud(reader.id, "Basic YWxpY2U6YXBw")
        LookingBackViewModel(reader.id, fixture.accounts, NextcloudExtras(fixture.clients)).also { store.put("lb", it) }
    }

    @Test
    fun `memories come newest first, and turning the recap on brings its counts`() = runBlocking {
        val viewModel = open(Memories(refuse = false))
        val ready = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        assertEquals(listOf("2", "1"), ready.memories.map { it.statusId })
        assertEquals(listOf("", "Two years back"), ready.memories.map { it.text })
        assertEquals(1, ready.memories.first().attachments)
        viewModel.onRecap(true)
        val on = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.recap?.thisWeek == 3 } }
        assertEquals(WeeklyRecap(enabled = true, thisWeek = 3, lastWeek = 1), on.recap)
    }

    @Test
    fun `a switch the server refused goes back, and says so`() = runBlocking {
        val viewModel = open(Memories(refuse = true))
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        viewModel.onRecap(true)
        val back = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.changeFailed } }
        assertEquals(false, back.recap?.enabled)
        assertTrue(back.status == PageStatus.Ready)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
