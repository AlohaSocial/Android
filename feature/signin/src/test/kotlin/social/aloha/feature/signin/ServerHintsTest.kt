// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerHintsTest {
    @Test
    fun `an invite link is recognised, and nothing else is`() {
        assertEquals("https://mastodon.social/invite/AbC123", inviteOf(" https://mastodon.social/invite/AbC123 "))
        assertNull(inviteOf("https://mastodon.social/@alice"))
        assertNull(inviteOf("http://mastodon.social/invite/AbC123"))
        assertNull(inviteOf("see https://mastodon.social/invite/AbC123"))
    }

    @Test
    fun `an Aloha Social invite link is recognised, with or without index php`() {
        val link = "https://cloud.example/apps/social/signup?invite=Tok123"
        assertEquals(link, inviteOf(link))
        assertEquals(
            "https://cloud.example/index.php/apps/social/signup?invite=Tok123",
            inviteOf("https://cloud.example/index.php/apps/social/signup?invite=Tok123"),
        )
        assertNull(inviteOf("https://cloud.example/apps/social/signup"))
        assertNull(inviteOf("https://cloud.example/apps/social/signup?invite=Tok123&return=x"))
    }

    @Test
    fun `an invite's token is read for its own server only`() {
        assertEquals("Tok123", inviteToken("https://cloud.example/apps/social/signup?invite=Tok123", "cloud.example"))
        assertEquals("AbC123", inviteToken("https://cloud.example/invite/AbC123/", "cloud.example"))
        assertNull(inviteToken("https://cloud.example/apps/social/signup?invite=Tok123", "other.example"))
    }
}
