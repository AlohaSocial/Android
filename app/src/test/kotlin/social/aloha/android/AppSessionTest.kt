// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount

class AppSessionTest {
    private val alice = SignedInAccount(
        id = "a",
        host = "example.social",
        apiBase = "https://example.social",
        serverAccountId = "1",
        handle = "alice",
        displayName = "Alice",
        avatarUrl = null,
        headerUrl = null,
        capabilities = ServerCapabilities.minimal("4.3.0"),
        needsReauth = true,
        profilePending = false,
        addedAt = Instant.EPOCH,
        nextcloudConnected = false,
    )

    @Test
    fun `only the first sign-in starts with the welcome`() {
        assertEquals(AppSession.SigningIn(first = true), sessionOf(emptyList(), null, again = false, adding = false))
        assertEquals(AppSession.SigningIn(), sessionOf(listOf(alice), alice, again = true, adding = false))
        assertEquals(AppSession.SigningIn(adding = true), sessionOf(listOf(alice), alice, again = false, adding = true))
    }
}
