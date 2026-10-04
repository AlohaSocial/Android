// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.server

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.testing.SignedInFixture

/** A server with rules and a description, no privacy policy (404), no terms, and no peers. */
private class Published : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
        "/api/v1/instance/rules" -> json("""[{"id":"1","text":"Be kind","hint":"Really"}]""")
        "/api/v1/instance/extended_description" -> json("""{"content":"<p>A server by the sea</p>"}""")
        "/api/v1/instance/terms_of_service" -> json("""{"content":"  "}""")
        "/api/v1/instance/peers" -> json("[]")
        "/api/v1/instance/domain_blocks" -> json("""[{"domain":"spam.example","severity":"suspend"}]""")
        else -> MockResponse.Builder().code(404).body("""{"error":"Record not found"}""").build()
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}

@RunWith(RobolectricTestRunner::class)
class ServerInfoTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply {
        dispatcher = Published()
        start()
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `only the pages the administrator published are there`() = runBlocking {
        val about = ServerInfo(fixture.clients).about(fixture.signIn(server.url("/")))
        assertEquals("Be kind", about.rules?.single()?.text)
        assertEquals("<p>A server by the sea</p>", about.description?.content)
        assertNull(about.privacyPolicy)
        assertNull(about.termsOfService)
        assertNull(about.activity)
        assertNull(about.peers)
        assertEquals("spam.example", about.domainBlocks?.single()?.domain)
    }
}
