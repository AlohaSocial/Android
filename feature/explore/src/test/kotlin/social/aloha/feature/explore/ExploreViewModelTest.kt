// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.explore.Explore
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.html.RichTextCache
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

/** Suggests account 7, and refuses to forget it once [release] lets the refusal go. */
private class Stubborn : Dispatcher() {
    val release = CountDownLatch(1)

    override fun dispatch(request: RecordedRequest): MockResponse = when {
        request.url.encodedPath.endsWith("/api/v2/suggestions") -> MockResponse.Builder().code(200)
            .body("""[{"account":{"id":"7","username":"kai","acct":"kai"}}]""")
            .addHeader("content-type", "application/json").build()

        // the dismiss, held until the test has seen the suggestion go
        request.method == "DELETE" -> {
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            MockResponse.Builder().code(500).body("{}").build()
        }

        else -> MockResponse.Builder().code(500).body("{}").build()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExploreViewModelTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = Stubborn()
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
    fun `a suggestion dismissed goes at once, and comes back where the server would not forget it`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val reader = fixture.signIn(server.url("/"))
        val explore =
            ExploreViewModel(
                reader.id,
                fixture.accounts,
                Explore(fixture.clients, fixture.statuses),
                RichTextCache(),
                ReadingPreferences(InMemoryDataStore(emptyPreferences())),
            )
        // the account is looked up off the test's thread; picking the tab meanwhile loads People twice, and
        // the second answer could bring the dismissed suggestion back before the server refused
        withTimeout(10.seconds) { explore.uiState.first { it.viewer.isNotEmpty() } }
        explore.onTab(ExploreTab.People)
        val people = withTimeout(10.seconds) { explore.uiState.first { it.people is Load.Loaded } }
        assertEquals(listOf("7"), (people.people as Load.Loaded).value.suggestions.map { it.id })
        explore.onDismiss("7")
        // gone at once, while the server is still being asked
        assertEquals(emptyList<String>(), (explore.uiState.value.people as Load.Loaded).value.suggestions.map { it.id })
        answers.release.countDown()
        // it is suggested again once the server refuses, where it was
        val after = withTimeout(10.seconds) {
            explore.uiState.first { (it.people as? Load.Loaded)?.value?.suggestions?.size == 1 }
        }
        assertEquals(listOf("7"), (after.people as Load.Loaded).value.suggestions.map { it.id })
    }
}
