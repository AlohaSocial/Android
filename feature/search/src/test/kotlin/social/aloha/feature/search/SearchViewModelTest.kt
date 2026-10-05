// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.search

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.search.Searches
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.html.RichTextCache
import social.aloha.core.navigation.SearchKey
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.ui.RichTextColors

/** Every search finds post 9, alone. */
private class OnePost : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.url.encodedPath.endsWith("/api/v2/search")) asked += request.url.queryParameter("q").orEmpty()
        val body = if (request.url.encodedPath.endsWith("/api/v2/search")) {
            """{"accounts":[],"hashtags":[],"statuses":[{"id":"9","created_at":"2026-09-29T10:00:00Z",""" +
                """"content":"<p>hi</p>","account":{"id":"6","username":"alice","acct":"alice"}}]}"""
        } else {
            "{}"
        }
        return MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = OnePost()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    @Test
    fun `an address submitted opens the one post it names, searched once`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val reader = fixture.signIn(server.url("/"))
        val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
        val model = SearchViewModel(
            SearchKey(reader.id),
            fixture.accounts,
            Searches(fixture.clients, fixture.statuses, settings),
            RemoteLookup(fixture.clients, fixture.statuses),
            RichTextCache(),
        )
        model.onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
        val watching = launch { model.uiState.collect {} }
        val address = "https://social.example/@alice/9"
        model.onQuery(address)
        model.onSubmit()
        val found = withTimeout(10.seconds) { model.uiState.first { it.found != null } }.found
        assertEquals(Found.Post("9"), found)
        assertEquals(listOf(address), answers.asked)
        watching.cancel()
    }
}
