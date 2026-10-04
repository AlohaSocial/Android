// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme

/** Tabs over pages: a swipe and a tap both move them, and the row stays while a page scrolls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TabPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            var selected by remember { mutableStateOf("One") }
            AlohaTheme {
                TabPager(
                    listOf("One", "Two", "Three"),
                    selected,
                    {
                        chosen += it
                        selected = it
                    },
                    { it },
                ) { tab ->
                    LazyColumn(Modifier.fillMaxSize().testTag("page $tab")) {
                        items((0 until ROWS).toList()) { Text("$tab row $it") }
                    }
                }
            }
        }
    }

    @Test
    fun `a swipe across moves to the next tab, and a tap jumps`() {
        show()
        compose.onNodeWithTag("page One").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Two row 0").assertIsDisplayed()
        compose.onNodeWithText("Two").assertIsSelected()
        compose.onNodeWithText("Three").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Three row 0").assertIsDisplayed()
        compose.onNodeWithText("Three").assertIsSelected()
        assertEquals(listOf("Two", "Three"), chosen)
    }

    @Test
    fun `the row stays in view however far a page scrolls`() {
        show()
        compose.onNodeWithTag("page One").performScrollToIndex(ROWS - 1)
        compose.onNodeWithText("Two").assertIsDisplayed()
    }

    private companion object {
        const val ROWS = 60
    }
}
