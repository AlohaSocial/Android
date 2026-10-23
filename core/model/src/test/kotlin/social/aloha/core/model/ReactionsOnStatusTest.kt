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

    @Test
    fun `the account's own pages take the sign-in token from 0,26,220 on, and only on Nextcloud Social`() {
        fun pages(name: String, version: String) =
            ServerCapabilities.minimal("https://x.test/").copy(softwareName = name, softwareVersion = version)
                .accountPagesWithToken
        assertEquals(false, pages("nextcloud-social", "0.26.219"))
        assertEquals(true, pages("nextcloud-social", "0.26.220"))
        assertEquals(false, pages("nextcloud-social", "dev"))
        assertEquals(false, pages("mastodon", "4.5.0"))
    }

    @Test
    fun `views are counted from 0,26,278 on, and only on Nextcloud Social`() {
        fun views(name: String, version: String) =
            ServerCapabilities.minimal("https://x.test/").copy(softwareName = name, softwareVersion = version)
                .videoViews
        assertEquals(false, views("nextcloud-social", "0.26.277"))
        assertEquals(true, views("nextcloud-social", "0.26.278"))
        assertEquals(false, views("mastodon", "4.5.0"))
    }
}
