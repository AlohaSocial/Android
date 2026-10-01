// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.explore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
import social.aloha.core.data.Answer
import social.aloha.core.testing.SignedInFixture

/** Suggestions only: the starter packs and popular accounts routes are not served. */
private class SuggestionsOnly(private val suggestions: Boolean = true) : Dispatcher() {
    val asked = mutableListOf<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.url
        asked += url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        return when {
            suggestions && url.encodedPath.endsWith("/api/v2/suggestions") -> json(
                """[{"source":"global","account":{"id":"7","username":"kai","acct":"kai"}}]""",
            )

            url.encodedPath.endsWith(
                "/trends/tags",
            ) -> json("""[{"name":"surf","history":[{"day":"1","uses":"12","accounts":"0"}]}]""")

            else -> MockResponse.Builder().code(404).body("{}").build()
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}

@RunWith(RobolectricTestRunner::class)
class ExploreTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val explore = Explore(fixture.clients, fixture.statuses)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `who to follow is what the server serves, a route it does not serve left empty`() = runBlocking {
        server.dispatcher = SuggestionsOnly()
        val people = (explore.people(fixture.signIn(server.url("/"))) as Answer.Got).value
        assertEquals(listOf("7"), people.suggestions.map { it.id })
        assertTrue(people.packs.isEmpty() && people.popular.isEmpty())
    }

    @Test
    fun `only when every route fails is who to follow a failure`() = runBlocking {
        server.dispatcher = SuggestionsOnly(suggestions = false)
        assertTrue(explore.people(fixture.signIn(server.url("/"))) is Answer.Missed)
    }

    @Test
    fun `trending hashtags are asked for over the period picked`() = runBlocking {
        val answers = SuggestionsOnly()
        server.dispatcher = answers
        val tags = (explore.hashtags(fixture.signIn(server.url("/")), period = "3d") as Answer.Got).value
        assertEquals(12, tags.single().history.single().usesCount)
        assertTrue(answers.asked.any { it.contains("/trends/tags") && it.contains("period=3d") })
    }
}
