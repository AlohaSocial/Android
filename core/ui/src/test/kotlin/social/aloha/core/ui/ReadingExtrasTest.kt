// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import android.icu.util.ULocale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.model.ReadingStyle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReadingExtrasTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-05T16:00:00Z")
    private val british = ULocale.UK

    private fun time(at: String, hours24: Boolean = true) =
        absoluteTime(Instant.parse(at), now, hours24, ZoneOffset.UTC, british)

    @Test
    fun `today's post shows its time, this year's its day, an older one its date`() {
        assertEquals("14:32", time("2026-10-05T14:32:00Z"))
        assertEquals("3 Oct", time("2026-10-03T09:00:00Z"))
        assertEquals("3 Oct 2025", time("2025-10-03T09:00:00Z"))
    }

    @Test
    fun `a twelve-hour phone gets a twelve-hour clock`() {
        assertEquals("2:32 pm", time("2026-10-05T14:32:00Z", hours24 = false))
    }

    @Test
    fun `the chosen text size multiplies text on top of the system's, and leaves dp alone`() {
        var text = 0f
        var space = 0f
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(2f, 1.2f),
                LocalReadingStyle provides ReadingStyle(textScale = 1.5f),
            ) {
                ReadingTextSize {
                    with(LocalDensity.current) {
                        text = 10.sp.toPx()
                        space = 10.dp.toPx()
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(10 * 1.2f * 1.5f * 2f, text, 0.01f)
        assertEquals(20f, space, 0.01f)
    }
}
