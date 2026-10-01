// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_J
import android.view.KeyEvent.KEYCODE_L
import android.view.KeyEvent.KEYCODE_SLASH
import android.view.KeyEvent.META_CTRL_ON
import android.view.KeyEvent.META_SHIFT_ON
import androidx.compose.ui.input.key.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimelineCommandTest {
    private fun key(code: Int, meta: Int = 0, action: Int = ACTION_DOWN) =
        KeyEvent(android.view.KeyEvent(0, 0, action, code, 0, meta))

    @Test
    fun `a bare letter is a command, a held modifier leaves it to the system`() {
        assertEquals(TimelineCommand.Next, timelineCommandOf(key(KEYCODE_J)))
        assertNull(timelineCommandOf(key(KEYCODE_J, META_CTRL_ON)))
        assertNull(timelineCommandOf(key(KEYCODE_J, action = ACTION_UP)))
        assertEquals(TimelineCommand.Favourite, timelineCommandOf(key(KEYCODE_L)))
        assertEquals(TimelineCommand.Help, timelineCommandOf(key(KEYCODE_SLASH, META_SHIFT_ON)))
        assertNull(timelineCommandOf(key(KEYCODE_SLASH)))
    }

    @Test
    fun `the selection starts at the first post in sight and stops at either end`() {
        val posts = listOf("a", "b", "c")
        assertEquals("b", moveSelection(TimelineCommand.Next, posts, null, "b"))
        assertEquals("a", moveSelection(TimelineCommand.Previous, posts, "gone", null))
        assertEquals("c", moveSelection(TimelineCommand.Next, posts, "b", "a"))
        assertNull(moveSelection(TimelineCommand.Next, posts, "c", "a"))
        assertNull(moveSelection(TimelineCommand.Previous, posts, "a", "a"))
        assertNull(moveSelection(TimelineCommand.Next, emptyList(), null, null))
    }
}
