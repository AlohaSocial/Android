// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.mediaviewer

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.MediaAttachment
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MediaViewerTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = object : MediaViewerActions {
        override fun onClose() {
            asked += "close"
        }

        override fun onSave(attachment: MediaAttachment) {
            asked += "save"
        }

        override fun onShare(attachment: MediaAttachment) {
            asked += "share"
        }

        override fun onCopy(attachment: MediaAttachment) {
            asked += "copy"
        }

        override fun onOpenInBrowser(attachment: MediaAttachment) {
            asked += "browser"
        }

        override fun onReport() {
            asked += "report"
        }
    }

    private val pictures =
        listOf(StatusSamples.image("a"), StatusSamples.image("b", alt = null), StatusSamples.image("c"))

    private fun show(start: Int) {
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Dark)) { MediaViewer(pictures, start, { emptyList() }, actions) }
        }
    }

    @Test
    fun `zooming and closing are buttons too, for whoever cannot pinch or drag`() {
        show(start = 0)
        compose.onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom in").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Zoom out").assertIsEnabled()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("close"), asked)
    }

    @Test
    fun `the ALT badge shows the author's description`() {
        show(start = 0)
        compose.onNodeWithText("ALT").performClick()
        compose.onNodeWithText("A sunny beach").assertExists()
    }

    @Test
    fun `a picture without a description has no ALT badge at all`() {
        show(start = 1)
        compose.onNodeWithText("ALT").assertDoesNotExist()
    }

    @Test
    fun viewer() {
        compose.enableAccessibilityChecks()
        show(start = 0)
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/media-viewer.png")
    }
}
