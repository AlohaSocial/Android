// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.translation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.network.ApiError
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class TranslationRepositoryTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `the post is asked for in the reader's language`() = runBlocking {
        server.enqueue(json(200, """{"content":"<p>Hello</p>","detected_source_language":"de","provider":"DeepL"}"""))
        val reader = fixture.signIn(server.url("/"))
        val answer = TranslationRepository(fixture.clients).translate(reader, "7", "en") as Answer.Got
        assertEquals("<p>Hello</p>", answer.value.content)
        assertEquals("DeepL", answer.value.provider)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/statuses/7/translate", request.url.encodedPath)
        assertEquals("lang=en", request.body?.utf8())
    }

    @Test
    fun `a server without a translation service says so in its own words`() = runBlocking {
        // Nextcloud Social's answer on an instance with no provider, verified on the dev instance
        server.enqueue(json(503, """{"error":"no translation provider is configured"}"""))
        val reader = fixture.signIn(server.url("/"))
        val answer = TranslationRepository(fixture.clients).translate(reader, "7", "en") as Answer.Missed
        assertEquals(ApiError.Server(503, "no translation provider is configured"), answer.error)
    }

    @Test
    fun `only a post in a language the reader does not read is offered`() {
        val server = ServerCapabilities.minimal("https://example.social/").copy(translation = true)
        assertTrue(server.offersTranslation("de", listOf("en-GB", "fr")))
        assertFalse(server.offersTranslation("fr", listOf("en-GB", "fr")))
        assertFalse(server.offersTranslation("en-US", listOf("en-GB")))
        assertFalse(server.offersTranslation(null, listOf("en")))
        assertFalse(server.copy(translation = false).offersTranslation("de", listOf("en")))
    }

    @Test
    fun `a server listing its language pairs is offered only those`() {
        val server = ServerCapabilities.minimal("https://example.social/")
            .copy(translation = true, translationLanguages = mapOf("de" to listOf("en", "fr"), "ja" to listOf("fr")))
        assertTrue(server.offersTranslation("de", listOf("en")))
        assertFalse(server.offersTranslation("ja", listOf("en")))
        assertFalse(server.offersTranslation("ko", listOf("en")))
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
}
