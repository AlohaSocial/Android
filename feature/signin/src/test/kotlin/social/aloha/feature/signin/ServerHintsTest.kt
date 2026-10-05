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
}
