// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class PushInstanceTest {
    @Test
    fun `a push registration names its account, and says when it is the Nextcloud one`() {
        assertEquals("a1" to false, PushInstance.of("a1"))
        assertEquals("a1" to true, PushInstance.of(PushInstance.nextcloud("a1")))
    }
}
