// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BlockedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = BlockedState(
        blocked = listOf(Kept("7", "Troll", "@troll@spam.example", null)),
        muted = emptyList(),
        servers = listOf("spam.example"),
    )

    @Test
    fun `each blocked account has its way back`() {
        val taken = mutableListOf<String>()
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme { BlockedScreen(state, BlockedActions(onUnblock = { taken += it }), {}, {}) }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/blocked.png")
        compose.onNodeWithText("Unblock").performClick()
        assertEquals(listOf("7"), taken)
    }

    @Test
    fun `the servers tab takes a server to block`() {
        compose.setContent {
            AlohaTheme { BlockedScreen(state, BlockedActions(), {}, {}, initialTab = BlockedTab.Servers) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/blocked-servers.png")
    }
}
