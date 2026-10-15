// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class WatchHandoverTest {
    @Test
    fun `a token's digest is its SHA-256 in lower-case hex, and two tokens never share one`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            WatchHandover.digest("abc"),
        )
        assertNotEquals(WatchHandover.digest("phone-token"), WatchHandover.digest("phone-token-2"))
    }
}
