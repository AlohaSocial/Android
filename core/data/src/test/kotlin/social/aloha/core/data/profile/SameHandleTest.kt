// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameHandleTest {
    @Test
    fun `a remote handle matches only that account`() {
        assertTrue(sameHandle("bob@other.social", "Bob@other.social", "cloud.example"))
        assertFalse(sameHandle("bob@other.social", "bob@elsewhere.social", "cloud.example"))
    }

    @Test
    fun `an account of the reader's own server matches with or without its domain`() {
        assertTrue(sameHandle("alice", "alice@cloud.example", "cloud.example"))
        assertTrue(sameHandle("alice", "alice", "cloud.example"))
    }

    @Test
    fun `a local account never stands in for a remote one of the same name`() {
        assertFalse(sameHandle("alice", "alice@evil.example", "cloud.example"))
    }
}
