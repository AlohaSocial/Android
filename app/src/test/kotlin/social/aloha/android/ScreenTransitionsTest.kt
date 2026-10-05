// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScreenTransitionsTest {
    @Test
    fun `with motion reduced, every screen change is a jump`() {
        val reduced = ScreenTransitions(slide = 80, reduced = true)
        listOf(reduced.forward(), reduced.back(), reduced.predictiveBack()).forEach {
            assertEquals(EnterTransition.None, it.targetContentEnter)
            assertEquals(ExitTransition.None, it.initialContentExit)
        }
    }

    @Test
    fun `otherwise screens move along the axis both ways`() {
        val moving = ScreenTransitions(slide = 80, reduced = false)
        listOf(moving.forward(), moving.back(), moving.predictiveBack()).forEach {
            assertNotEquals(EnterTransition.None, it.targetContentEnter)
            assertNotEquals(ExitTransition.None, it.initialContentExit)
        }
    }
}
