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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.testing.SignedInFixture

/**
 * Channels as Social 0.26.181 answers: the list for a GET, and only the channel written for a POST or a
 * PUT, each wrapped. A taken handle is refused with its reason.
 */
private class Channels : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        asked += request.method
        val form = request.body?.utf8().orEmpty()
        return when {
            request.method == "POST" && form.contains("handle=taken") ->
                json(422, """{"error":"this handle is taken"}""")

            request.method == "POST" -> json(200, """{"channels":[{"id":"3","handle":"surf","name":"Surf"}]}""")

            request.method == "PUT" -> json(
                200,
                """{"channels":[{"id":"1","handle":"alice_channel","name":"Renamed"}]}""",
            )

            else -> json(
                200,
                """{"channels":[{"id":"1","handle":"alice_channel","name":"Alice's videos"},""" +
                    """{"id":"2","handle":"boards","name":"Boards"}]}""",
            )
        }
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ChannelsViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val store = ViewModelStore()
    private val channels = Channels()
    private val server = MockWebServer().apply {
        dispatcher = channels
        start()
    }

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    private fun open(): ChannelsViewModel = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        fixture.connectNextcloud(reader.id, "Basic YWxpY2U6YXBw")
        ChannelsViewModel(reader.id, fixture.accounts, NextcloudExtras(fixture.clients)).also { store.put("ch", it) }
    }

    @Test
    fun `a new channel joins the list and a renamed one keeps its place, the others staying`() = runBlocking {
        val viewModel = open()
        val ready = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        viewModel.onNew()
        viewModel.onDraft(ChannelDraft(handle = "surf", name = "Surf"))
        viewModel.onSave()
        val added = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.channels.size == 3 } }
        assertNull(added.editing)
        assertEquals(listOf("alice_channel", "boards", "surf"), added.channels.map { it.handle })
        viewModel.onEdit(ready.channels.first())
        viewModel.onDraft(viewModel.uiState.value.editing!!.copy(name = "Renamed"))
        viewModel.onSave()
        val renamed = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.channels.first().name == "Renamed" } }
        assertEquals(listOf("alice_channel", "boards", "surf"), renamed.channels.map { it.handle })
    }

    @Test
    fun `a refused handle keeps the editor open with the server's reason`() = runBlocking {
        val viewModel = open()
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        viewModel.onNew()
        viewModel.onDraft(ChannelDraft(handle = "taken", name = "Mine"))
        viewModel.onSave()
        val refused = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.saveFailure != null } }
        assertEquals("this handle is taken", refused.saveFailure)
        assertEquals("taken", refused.editing?.handle)
    }

    @Test
    fun `a new channel needs a name and a handle of letters, digits and underscores`() {
        assertTrue(ChannelDraft(handle = "surf_2026", name = "Surf").canSave)
        assertFalse(ChannelDraft(handle = "surf-2026", name = "Surf").canSave)
        assertFalse(ChannelDraft(handle = "surf", name = " ").canSave)
        assertFalse(ChannelDraft(handle = "a".repeat(65), name = "Surf").canSave)
        assertTrue(ChannelDraft(id = "1", handle = "old-style", name = "Renamed").canSave)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
