// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import social.aloha.core.model.MediaFocus

class FocalAlignmentTest {
    @Test
    fun `no focus crops around the centre`() {
        assertEquals(Alignment.Center, focalAlignment(null))
    }

    /** Mastodon's y points up, Compose's bias down: a focus near the top must keep the top in view. */
    @Test
    fun `a focus near the top left keeps the top left in view`() {
        assertEquals(BiasAlignment(-0.5f, -0.8f), focalAlignment(MediaFocus(-0.5, 0.8)))
    }

    @Test
    fun `a focus outside the image is held to its edge`() {
        assertEquals(BiasAlignment(1f, 1f), focalAlignment(MediaFocus(3.0, -2.0)))
    }
}
