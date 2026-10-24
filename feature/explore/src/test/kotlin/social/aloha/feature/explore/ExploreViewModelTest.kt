// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.StarterPack
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

    private fun viewModel(readerId: String): ExploreViewModel {
        val followed = FollowedAuthors(fixture.clients)
        return ExploreViewModel(
            readerId,
            fixture.accounts,
            Explore(fixture.clients, fixture.statuses),
            RichTextCache(),
            ReadingPreferences(InMemoryDataStore(emptyPreferences())),
            ProfileRepository(fixture.clients, fixture.statuses, followed),
            followed,
        )
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
        val explore = viewModel(reader.id)
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

    @Test
    fun `a starter pack that could not be followed says so once, and is not marked followed`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val reader = fixture.signIn(server.url("/"))
        val explore = viewModel(reader.id)
        withTimeout(10.seconds) { explore.uiState.first { it.viewer.isNotEmpty() } }
        explore.onFollowAll("surfers")
        val failed = withTimeout(10.seconds) { explore.uiState.first { it.followFailed } }
        assertEquals(emptySet<String>(), failed.followed)
        explore.onFollowFailedShown()
        assertEquals(false, explore.uiState.value.followFailed)
    }

    @Test
    fun `an open starter pack says whom the reader follows, and learns it again once the pack is followed`() =
        runBlocking {
            Dispatchers.setMain(Dispatchers.Unconfined)
            val pack = PackServer()
            server.dispatcher = pack
            val reader = fixture.signIn(server.url("/"))
            val explore = viewModel(reader.id)
            withTimeout(10.seconds) { explore.uiState.first { it.viewer.isNotEmpty() } }
            explore.packs.open(StarterPack("surf", "Surf"))
            val open = withTimeout(10.seconds) { explore.uiState.first { it.following.isNotEmpty() } }
            assertEquals(listOf("1", "7"), (open.sheet?.members as Load.Loaded).value.accounts.map { it.id })
            assertEquals(mapOf("1" to true, "7" to false), open.following)
            explore.packs.followMember(Account("7", "kai", "kai@waves.example"))
            val requested = withTimeout(10.seconds) { explore.uiState.first { it.sheet?.requested == setOf("7") } }
            assertEquals(emptySet<String>(), requested.sheet?.pending)
            explore.onFollowAll("surf")
            withTimeout(10.seconds) { explore.uiState.first { "surf" in it.followed } }
            withTimeout(10.seconds) { while (pack.relationships.get() < 2) yield() }
            explore.packs.close()
            assertEquals(null, explore.uiState.value.sheet)
        }
}

/** The pack Surf holding accounts 1 and 7, the reader following 1, and follows that wait on approval. */
private class PackServer : Dispatcher() {
    val relationships = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            path.endsWith("/api/v1/starter_packs/surf") -> json(
                """{"id":"surf","name":"Surf","accounts":[{"id":"1","username":"alice","acct":"alice"},
                    {"id":"7","username":"kai","acct":"kai@waves.example"}]}""",
            )

            path.endsWith("/api/v1/accounts/relationships") -> {
                relationships.incrementAndGet()
                json("""[{"id":"1","following":true},{"id":"7","following":false}]""")
            }

            path.endsWith("/api/v1/accounts/7/follow") -> json("""{"id":"7","following":false,"requested":true}""")

            path.endsWith("/api/v1/starter_packs/surf/follow") -> json("""{"followed":["alice"]}""")

            else -> MockResponse.Builder().code(404).body("{}").build()
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}
