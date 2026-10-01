// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WindowActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a new window is a task of its own, beside this one, opening the post or profile as the reader`() {
        val window = WindowActivity.intent(context, "a1", "s1", null)
        assertEquals(WindowActivity::class.java.name, window.component?.className)
        val flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        assertEquals(flags, window.flags and flags)
        assertTrue(window.getBooleanExtra(WindowActivity.EXTRA_WINDOW, false))
        // the window reads it as any request from outside, so the main activity's routing applies
        assertEquals(OutsideRequest.Open("a1", "s1", null), OutsideRequest.of(window, context.packageName))
        assertEquals(
            OutsideRequest.Open("a1", null, "p1"),
            OutsideRequest.of(WindowActivity.intent(context, "a1", null, "p1"), context.packageName),
        )
    }
}
