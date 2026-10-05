// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WindowActionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the window offers to pause a playing video, and to play a paused one, between the skips`() {
        assertEquals(
            listOf("Back 10 seconds", "Pause", "Forward 10 seconds"),
            windowActions(context, playing = true).map { it.title.toString() },
        )
        assertEquals("Play", windowActions(context, playing = false)[1].title.toString())
    }
}
