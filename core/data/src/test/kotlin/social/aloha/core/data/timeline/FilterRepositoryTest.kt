// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.network.endpoints.FilterDraft
import social.aloha.core.network.endpoints.KeywordDraft
import social.aloha.core.testing.SignedInFixture

/** Answers a create with filter 9 and a delete with nothing; anything else is refused. */
private class FilterServer : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse = when {
        request.method == "POST" && request.url.encodedPath.endsWith("/api/v2/filters") -> MockResponse.Builder()
            .code(200)
            .addHeader("content-type", "application/json")
            .body(
                """{"id":"9","title":"Spoilers","context":["home"],"filter_action":"hide",""" +
                    """"keywords":[{"id":"1","keyword":"finale","whole_word":true}],"statuses":[]}""",
            )
            .build()

        request.method == "DELETE" -> MockResponse.Builder().code(200).body("{}").build()

        else -> MockResponse.Builder().code(500).build()
    }
}

@RunWith(RobolectricTestRunner::class)
class FilterRepositoryTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply {
        dispatcher = FilterServer()
        start()
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `a filter saved here applies at once, and one deleted stops at once, without a refetch`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val draft = FilterDraft(
            "Spoilers",
            listOf(FilterContext.Home),
            FilterAction.Hide,
            expiresInSeconds = null,
            keywords = listOf(KeywordDraft("finale", wholeWord = true)),
        )
        assertTrue(fixture.filters.save(reader, null, draft) is Answer.Got)
        assertEquals(listOf("Spoilers"), fixture.filters.observe(reader.id).first().map { it.title })
        assertTrue(fixture.filters.delete(reader, "9") is Answer.Got)
        assertTrue(fixture.filters.observe(reader.id).first().isEmpty())
    }

    @Test
    fun `a save the server refuses keeps nothing`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val draft = FilterDraft("Spoilers", listOf(FilterContext.Home), FilterAction.Warn, null, emptyList())
        assertTrue(fixture.filters.save(reader, "4", draft) is Answer.Missed)
        assertTrue(fixture.filters.observe(reader.id).first().isEmpty())
    }
}
