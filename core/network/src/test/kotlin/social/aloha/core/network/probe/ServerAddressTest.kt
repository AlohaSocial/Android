// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.probe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerAddressTest {
    @Test
    fun `bare hosts, URLs, paths and handles all resolve to a host`() {
        listOf(
            "cloud.example.com",
            "https://cloud.example.com/",
            "  CLOUD.Example.COM ",
            "@alice@cloud.example.com",
            "alice@cloud.example.com",
        ).forEach { assertEquals("cloud.example.com", ServerAddress.parse(it)?.host, it) }
    }

    @Test
    fun `a typed path is kept as a hint, not assumed`() {
        val address = ServerAddress.parse("cloud.example.com/nextcloud/")
        assertEquals("cloud.example.com", address?.host)
        assertEquals("nextcloud", address?.pathHint)
    }

    @Test
    fun `nonsense is refused`() {
        assertNull(ServerAddress.parse(""))
        assertNull(ServerAddress.parse("not a host"))
        assertNull(ServerAddress.parse("@alice@"))
    }

    @Test
    fun `plain HTTP is refused unless cleartext is allowed`() {
        assertNull(ServerAddress.parse("http://cloud.example.com"))
        assertEquals(
            "http://nextcloud.local/",
            ServerAddress.parse("http://nextcloud.local", allowCleartext = true)?.origin.toString(),
        )
    }

    @Test
    fun `a non-default port survives`() {
        assertEquals(8443, ServerAddress.parse("cloud.example.com:8443")?.origin?.port)
    }

    @Test
    fun `the five candidates appear in rank order`() {
        val paths = candidatesFor(ServerAddress.parse("cloud.example.com/nextcloud") ?: error("parse")).map {
            it.base.encodedPath
        }
        assertEquals(
            listOf("/", "/index.php/apps/social/", "/apps/social/", "/nextcloud/", "/nextcloud/index.php/apps/social/"),
            paths,
        )
    }

    @Test
    fun `without a path hint there are only three candidates, every one ending in a slash`() {
        val candidates = candidatesFor(ServerAddress.parse("mastodon.example") ?: error("parse"))
        assertEquals(listOf(1, 2, 3), candidates.map { it.rank })
        assertTrue(candidates.all { it.base.encodedPath.endsWith("/") })
    }
}
