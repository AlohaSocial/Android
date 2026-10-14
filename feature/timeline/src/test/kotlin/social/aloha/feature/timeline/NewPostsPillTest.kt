// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NewPostsPillTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the pill leaves saying what it said, never nought new posts`() {
        var count by mutableIntStateOf(3)
        compose.setContent { AlohaTheme { NewPostsPill(count, emptyList(), onReveal = { count = 0 }, Modifier) } }
        compose.onNodeWithText("3 new posts").assertExists()
        compose.mainClock.autoAdvance = false
        count = 0
        // mid-way through leaving
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(EXIT_MIDWAY)
        compose.onNodeWithText("0 new posts").assertDoesNotExist()
        compose.onNodeWithText("3 new posts").assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("3 new posts").assertDoesNotExist()
    }

    private companion object {
        const val EXIT_MIDWAY = 50L
    }
}
