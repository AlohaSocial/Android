// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.stories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class StoriesTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val noon = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val stories = Stories(fixture.clients, noon)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private fun story(id: String, account: String, seen: Boolean, created: String = "2026-09-29T08:00:00Z") =
        """{"id":"$id","account":{"id":"$account","username":"u$account","acct":"u$account"},""" +
            """"created_at":"$created","seen":$seen,"media":{"id":"m$id","type":"image"}}"""

    @Test
    fun `the rail is one reel per poster, the reader's own first, in the server's order, live ones only`() =
        runBlocking {
            val carousel = listOf(
                story("1", "7", seen = true),
                story("2", "6", seen = true),
                story("3", "8", seen = false),
                story("4", "7", seen = false),
                // a day and more old: gone, whatever the server still sends
                story("5", "9", seen = false, created = "2026-09-28T08:00:00Z"),
            ).joinToString(",", "[", "]")
            server.enqueue(
                MockResponse.Builder().code(200).body(carousel).addHeader("content-type", "application/json").build(),
            )
            val reels = (stories.rail(fixture.signIn(server.url("/"))) as Answer.Got).value
            assertEquals(listOf("6", "7", "8"), reels.map { it.account.id })
            assertEquals(listOf(true, false, false), reels.map { it.own })
            assertEquals(listOf("1", "4"), reels[1].stories.map { it.id })
            // one unseen story makes the whole reel new
            assertEquals(listOf(false, true, true), reels.map { it.unseen })
        }

    @Test
    fun `ending a story early falls back to Pixelfed's route where the server has no DELETE`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(
            MockResponse.Builder().code(200).body("{}").addHeader("content-type", "application/json").build(),
        )
        assertEquals(Answer.Got(Unit), stories.delete(fixture.signIn(server.url("/")), "5"))
        assertEquals("DELETE /api/v1/stories/5", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(
            "POST /api/v1.1/stories/self-expire/5",
            server.takeRequest().let { "${it.method} ${it.url.encodedPath}" },
        )
    }
}
