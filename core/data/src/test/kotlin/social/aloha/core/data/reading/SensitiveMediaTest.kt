// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.reading

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class SensitiveMediaTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val media = SensitiveMedia(fixture.clients)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `media is covered until the server says otherwise, then shown as it says`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        assertEquals(SensitiveMediaPolicy.Blur, media.policy(reader.id).first())
        server.enqueue(json(200, """{"reading:expand:media":"show_all"}"""))
        media.refresh(reader)
        assertEquals(SensitiveMediaPolicy.ShowAll, media.policy(reader.id).first())
    }

    @Test
    fun `a value this build does not know covers`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        server.enqueue(json(200, """{"reading:expand:media":"show_everything_twice"}"""))
        media.refresh(reader)
        assertEquals(SensitiveMediaPolicy.Blur, media.policy(reader.id).first())
    }

    @Test
    fun `a choice the server takes is kept, one it refuses changes nothing`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        server.enqueue(json(200, "{}"))
        assertTrue(media.choose(reader, SensitiveMediaPolicy.HideAll))
        val sent = server.takeRequest()
        assertEquals("PUT", sent.method)
        assertEquals("/api/v1/preferences", sent.url.encodedPath)
        assertEquals("expandMedia=hide_all", sent.body?.utf8())
        assertEquals(SensitiveMediaPolicy.HideAll, media.policy(reader.id).first())
        // Mastodon has no such route
        server.enqueue(json(404, """{"error":"Record not found"}"""))
        assertFalse(media.choose(reader, SensitiveMediaPolicy.ShowAll))
        assertEquals(SensitiveMediaPolicy.HideAll, media.policy(reader.id).first())
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
}
