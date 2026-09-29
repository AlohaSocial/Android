// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkHeaderTest {
    @Test
    fun `both relations parse from Mastodon's own form`() {
        val link = LinkHeader.parse(
            "<https://cloud.example/api/v1/timelines/home?limit=20&max_id=41>; rel=\"next\", " +
                "<https://cloud.example/api/v1/timelines/home?limit=20&min_id=60>; rel=\"prev\"",
        )
        assertEquals("limit=20&max_id=41", link.next?.query)
        assertEquals("limit=20&min_id=60", link.previous?.query)
    }

    @Test
    fun `a non-root API base survives in the cursor`() {
        val link = LinkHeader.parse(
            "<https://cloud.example/index.php/apps/social/api/v1/timelines/home?max_id=41>; rel=\"next\"",
        )
        assertEquals("/index.php/apps/social/api/v1/timelines/home", link.next?.encodedPath)
    }

    @Test
    fun `filters the caller sent survive into the cursor`() {
        val link = LinkHeader.parse(
            "<https://cloud.example/api/v1/timelines/public?only_video=true&max_id=9>; rel=\"next\"",
        )
        assertEquals("true", link.next?.queryParameter("only_video"))
    }

    @Test
    fun `a comma inside a query value does not split the header`() {
        val link = LinkHeader.parse("<https://cloud.example/api/v1/x?types=a,b,c&max_id=5>; rel=\"next\"")
        assertNotNull(link.next)
        assertNull(link.previous)
    }

    @Test
    fun `absent, empty and malformed headers are empty rather than fatal`() {
        assertTrue(LinkHeader.parse(null).isEmpty)
        assertTrue(LinkHeader.parse("").isEmpty)
        assertTrue(LinkHeader.parse("garbage").isEmpty)
        assertTrue(LinkHeader.parse("<not a url; rel=\"next\"").isEmpty)
    }

    @Test
    fun `unquoted and single-quoted rel values parse`() {
        assertNotNull(LinkHeader.parse("<https://x.test/a>; rel=next").next)
        assertNotNull(LinkHeader.parse("<https://x.test/a>; rel='prev'").previous)
    }

    @Test
    fun `the header decides whether more exists, not the row count`() {
        assertTrue(Paginated(listOf(1), LinkHeader(next = "https://x.test/2".toHttpUrl()), 1, 20).mayHaveMore)
        assertTrue(Paginated(listOf(1), LinkHeader(), rawCount = 20, requestedLimit = 20).mayHaveMore)
        assertFalse(Paginated(listOf(1), LinkHeader(), rawCount = 3, requestedLimit = 20).mayHaveMore)
    }
}
