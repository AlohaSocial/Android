// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.data.ServerLookup
import social.aloha.core.network.RateLimiter

/** What a build that refuses `http://` answers before any request is made, and what a found server shows. */
class ServerFinderTest {
    private val finder = testServerFinder(OkHttpClient(), RateLimiter(nowMillis = { 0L }), cleartextAllowed = false)

    @Test
    fun `an http address is refused as insecure, not as malformed`() = runBlocking {
        assertEquals(ServerLookup.InsecureAddress, finder.find("http://cloud.example"))
        assertEquals(ServerLookup.InsecureAddress, finder.find("HTTP://cloud.example/social"))
        assertEquals(ServerLookup.InsecureAddress, finder.findAt("http://cloud.example/index.php/apps/social/"))
    }

    @Test
    fun `text that is no address stays invalid, with or without a scheme`() = runBlocking {
        assertEquals(ServerLookup.InvalidAddress, finder.find("not a host"))
        assertEquals(ServerLookup.InvalidAddress, finder.find("http://not a host"))
        assertEquals(ServerLookup.InvalidAddress, finder.findAt("http://"))
    }

    @Test
    fun `a found server's texts are trimmed`() = runBlocking {
        MockSocialServer(MockServerConfiguration.Mastodon).start().use { mock ->
            val server = testServerFinder(OkHttpClient(), RateLimiter(nowMillis = { 0L })).found(mock.origin.toString())
            // mastodon.social sends its description with trailing blank lines
            assertEquals(
                "The original server of Mastodon, operated by Mastodon GmbH for the common good.",
                server.description,
            )
            assertTrue(server.rules.isNotEmpty() && server.rules.all { it == it.trim() })
        }
    }
}
