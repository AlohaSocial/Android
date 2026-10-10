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

/** Two apps as Social 0.26.181 lists them: `created_at` in seconds, `signed_in` a flag. */
private const val APPS = """[{"id":37,"name":"Aloha Social","website":"https://aloha.social",
"scopes":["read","write","follow","push"],"created_at":1791197994,"last_used_at":1791197994,"signed_in":true},
{"id":12,"name":"Tusky","website":null,"scopes":"read write","created_at":1790000000,"signed_in":true}]"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AuthorizedAppsViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val store = ViewModelStore()
    private val asked = CopyOnWriteArrayList<String>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += "${request.method} ${request.url.encodedPath}"
                val body = if (request.method == "GET") APPS else "{}"
                return MockResponse.Builder().body(body).addHeader("content-type", "application/json").build()
            }
        }
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

    private fun open(connected: Boolean): AuthorizedAppsViewModel = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        if (connected) fixture.connectNextcloud(reader.id, "Basic YWxpY2U6YXBw")
        AuthorizedAppsViewModel(reader.id, fixture.accounts, NextcloudExtras(fixture.clients))
            .also { store.put("apps", it) }
    }

    @Test
    fun `without the Nextcloud connection the page asks for it and asks the server nothing`() = runBlocking {
        val state =
            withTimeout(WAIT_MILLIS) { open(connected = false).uiState.first { it.status != PageStatus.Loading } }
        assertEquals(PageStatus.NeedsConnection, state.status)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `the apps are listed, and one revoked leaves the list`() = runBlocking {
        val viewModel = open(connected = true)
        val state = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        assertEquals(listOf("37", "12"), state.apps.map { it.id })
        assertEquals(listOf("read", "write"), state.apps[1].scopes)
        viewModel.onRevoke("12")
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.apps.size == 1 } }
        assertEquals("DELETE /api/v1/authorized_apps/12", asked.last())
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
