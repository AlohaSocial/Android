// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import android.icu.util.ULocale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PausingTest {
    @Test
    fun `a pause is named in its largest whole unit`() {
        assertEquals(
            listOf("30 minutes", "1 hour", "12 hours", "1 day", "3 days", "7 days"),
            PAUSES.map { pauseLength(it, ULocale.US) },
        )
    }
}
