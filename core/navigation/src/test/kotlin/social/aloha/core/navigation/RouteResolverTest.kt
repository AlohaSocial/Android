// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RouteResolverTest {
    @Test
    fun `a post's address, in each server's form, reads as a post`() {
        assertEquals(
            LinkTarget.Post("https://mastodon.social/@alice/117355", "mastodon.social", "117355"),
            RouteResolver.parse("https://mastodon.social/@alice/117355"),
        )
        assertEquals(
            LinkTarget.Post("https://cloud.example/users/bob/statuses/42", "cloud.example", "42"),
            RouteResolver.parse("https://cloud.example/users/bob/statuses/42"),
        )
        // a remote post seen through another server: its id there is that server's, not the post's own
        assertEquals(
            LinkTarget.Post("https://mastodon.social/@bob@remote.example/9", "mastodon.social", null),
            RouteResolver.parse("https://mastodon.social/@bob@remote.example/9"),
        )
        assertEquals(
            LinkTarget.Post("https://pleroma.example/notice/AbC1", "pleroma.example", null),
            RouteResolver.parse("https://pleroma.example/notice/AbC1"),
        )
    }

    @Test
    fun `a profile's address reads as user at server, and a hashtag as its name`() {
        assertEquals(
            LinkTarget.Profile("https://mastodon.social/@alice", "alice@mastodon.social"),
            RouteResolver.parse("https://mastodon.social/@alice"),
        )
        assertEquals(
            LinkTarget.Profile("https://m.test/@bob@remote.example", "bob@remote.example"),
            RouteResolver.parse("https://m.test/@bob@remote.example"),
        )
        assertEquals(
            LinkTarget.Profile("https://cloud.example/users/carol", "carol@cloud.example"),
            RouteResolver.parse("https://cloud.example/users/carol"),
        )
        assertEquals(LinkTarget.Tag("surf"), RouteResolver.parse("https://mastodon.social/tags/surf"))
    }

    @Test
    fun `the fediverse scheme and the app's own open link resolve like the address they carry`() {
        assertEquals(
            LinkTarget.Post("https://mastodon.social/@alice/117355", "mastodon.social", "117355"),
            RouteResolver.parse("web+ap://mastodon.social/@alice/117355"),
        )
        // an address already encoded stays as it was
        assertEquals(
            LinkTarget.Tag("café"),
            RouteResolver.parse("web+ap://mastodon.social/tags/caf%C3%A9"),
        )
        assertEquals("https://m.test/tags/caf%C3%A9?x=1", RouteResolver.browsable("web+ap://m.test/tags/caf%C3%A9?x=1"))
        assertEquals(
            LinkTarget.Profile("https://mastodon.social/@alice", "alice@mastodon.social"),
            RouteResolver.parse("alohasocial://open?url=https%3A%2F%2Fmastodon.social%2F%40alice"),
        )
    }

    @Test
    fun `anything else stays with the browser, and is never sent anywhere to look up`() {
        listOf(
            "https://example.com/articles/2026/aloha",
            "https://youtu.be/BgezCG4W_Ns",
            "https://example.com/",
            "https://blog.example/@author/aloha-at-last",
            "https://cloud.example/users/..%2F..",
            "javascript:alert(1)",
            "file:///data/data/social.aloha.android/databases/accounts.db",
            "alohasocial://open?url=http%3A%2F%2Fplain.example%2F%40alice",
            "alohasocial://oauth-callback/?code=x",
            "not a url at all",
        ).forEach { assertEquals(LinkTarget.Web, RouteResolver.parse(it), it) }
    }
}
