// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings

/** The settings list and a section at each width, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) { content() } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun sections() = capture("settings-sections") { SettingsScreen(listOf(AboutSection), onBack = {}, onSection = {}) }

    @Test
    @Config(fontScale = 2f)
    fun sectionsLargeFont() = capture("settings-sections-font200") {
        SettingsScreen(listOf(AboutSection), onBack = {}, onSection = {})
    }

    @Test
    fun about() = capture("settings-about") { SectionScreen(AboutSection, onBack = {}) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun aboutWide() = capture("settings-about-wide") { SectionScreen(AboutSection, onBack = {}) }
}
