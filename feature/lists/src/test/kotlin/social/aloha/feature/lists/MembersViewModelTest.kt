// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.lists

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.lists.Lists
import social.aloha.core.model.Account
import social.aloha.core.navigation.ListMembersKey
import social.aloha.core.testing.SignedInFixture

/**
 * Two lists, one of them a Nextcloud group's, with one member each; the group's refuses changes as
 * Nextcloud Social does, with its reason; every request remembered.
 */
private class ListServer : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} ${path.substringAfter("/api/v1/")}?${request.url.encodedQuery.orEmpty()}"
        return when {
            path.endsWith("/api/v1/lists") -> json(
                """[{"id":"1","title":"Surf","nextcloud_group":null},""" +
                    """{"id":"2","title":"Design team","nextcloud_group":"design"}]""",
            )

            request.method == "POST" && path.endsWith("/lists/2/accounts") -> MockResponse.Builder().code(422)
                .body("""{"error":"$REFUSAL"}""").addHeader("content-type", "application/json").build()

            path.endsWith("/accounts") && request.method == "GET" -> json("""[$BOB]""")

            path.endsWith("/accounts/search") -> json("""[$BOB,$KAI]""")

            else -> json("{}")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    companion object {
        const val REFUSAL = "This list follows the Nextcloud group design: its members are the group's"
        const val BOB = """{"id":"7","username":"bob","acct":"bob"}"""
        const val KAI = """{"id":"8","username":"kai","acct":"kai"}"""
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MembersViewModelTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = ListServer()
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

    private suspend fun open(listId: String): MembersViewModel {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val reader = fixture.signIn(server.url("/"))
        return MembersViewModel(ListMembersKey(reader.id, listId, "List"), fixture.accounts, Lists(fixture.clients))
    }

    private suspend fun MembersViewModel.await(predicate: (MembersUiState) -> Boolean): MembersUiState =
        withTimeout(10.seconds) { uiState.first(predicate) }

    @Test
    fun `a list is filled from those the reader follows, never offering someone already in it`() = runBlocking {
        val members = open("1")
        members.await { !it.loading && it.members.isNotEmpty() }
        members.onQuery("k")
        val found = members.await { it.found.isNotEmpty() }
        assertEquals(listOf("8"), found.found.map(Account::id))
        assertTrue(answers.asked.any { it.startsWith("GET accounts/search") && "following=true" in it })
    }

    @Test
    fun `a group list says so, and a change to it is refused in the server's own words`() = runBlocking {
        val members = open("2")
        val state = members.await { it.group && !it.loading }
        members.onAdd(state.members.first())
        val refused = members.await { it.refusal != null }
        assertEquals(Refusal.Said(ListServer.REFUSAL), refused.refusal)
    }
}
