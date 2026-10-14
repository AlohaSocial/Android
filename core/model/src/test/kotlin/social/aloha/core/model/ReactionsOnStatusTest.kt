// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReactionsOnStatusTest {
    private fun on(name: String, version: String) =
        ServerCapabilities.minimal("https://x.test/").copy(softwareName = name, softwareVersion = version)
            .reactionsOnStatus

    @Test
    fun `Nextcloud Social carries a post's reactions from 0,26,221 on`() {
        assertEquals(false, on("nextcloud-social", "0.26.220"))
        assertEquals(true, on("nextcloud-social", "0.26.221"))
        assertEquals(true, on("nextcloud-social", "0.26.251"))
        assertEquals(true, on("nextcloud-social", "0.27.0"))
        assertEquals(true, on("nextcloud-social", "1.0"))
        // the released versions, from the app store
        assertEquals(false, on("nextcloud-social", "0.24.1"))
    }

    @Test
    fun `an unreadable version, or another server, asks for the reactions on their own`() {
        assertEquals(false, on("nextcloud-social", ""))
        assertEquals(false, on("nextcloud-social", "dev"))
        assertEquals(true, on("nextcloud-social", "0.26.221-rc1"))
        assertEquals(false, on("mastodon", "4.5.0"))
    }
}
