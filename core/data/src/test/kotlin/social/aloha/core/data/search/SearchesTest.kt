// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class SearchesTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val searches =
        Searches(fixture.clients, fixture.statuses, AccountSettingsStore(InMemoryDataStore(emptyMap())))

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `an address or a full handle is fetched from where it lives, a word or a bare name is not`() {
        assertTrue(Searches.resolvable("https://pixelfed.example/p/alice/123"))
        assertTrue(Searches.resolvable("@alice@social.example"))
        assertTrue(Searches.resolvable("alice@social.example"))
        assertFalse(Searches.resolvable("alice"))
        assertFalse(Searches.resolvable("@alice"))
        assertFalse(Searches.resolvable("surfing at dawn"))
    }

    @Test
    fun `a pasted address asks the server to resolve it, and the post found is stored to act on`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val post = """{"id":"9","content":"<p>Hi</p>",""" +
            """"account":{"id":"2","username":"bob","acct":"bob@else.example"}}"""
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"accounts":[],"statuses":[$post],"hashtags":[]}""")
                .addHeader("content-type", "application/json").build(),
        )
        searches.search(reader, " https://else.example/@bob/9 ")
        val asked = server.takeRequest().url
        assertEquals("true", asked.queryParameter("resolve"))
        assertEquals("https://else.example/@bob/9", asked.queryParameter("q"))
        assertNotNull(fixture.statuses.observe(reader.id, listOf("9")).first()["9"])
    }

    @Test
    fun `the recent searches are newest first, once each whatever the case, and at most ten`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        (1..12).forEach { searches.remember(reader, "query $it") }
        searches.remember(reader, "QUERY 12")
        val recent = searches.recent(reader).first()
        assertEquals(10, recent.size)
        assertEquals(listOf("QUERY 12", "query 11"), recent.take(2))
        searches.clearRecent(reader)
        assertEquals(emptyList<String>(), searches.recent(reader).first())
    }
}
