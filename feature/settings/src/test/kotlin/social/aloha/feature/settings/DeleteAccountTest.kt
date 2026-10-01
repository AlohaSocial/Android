// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAccountTest {
    private val handle = "@Alice@cloud.example"

    @Test
    fun `the username confirms in any case and form, the domain is the server's to check`() {
        listOf("alice", "@alice", "ALICE@cloud.example", " @@alice@CLOUD.example ", "alice@social.example").forEach {
            assertTrue(it, DeleteAccountViewModel.confirms(it, handle))
        }
        listOf("", "@", "alic", "bob@cloud.example", "bob").forEach {
            assertFalse(it, DeleteAccountViewModel.confirms(it, handle))
        }
    }
}
