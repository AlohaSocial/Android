// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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

/** The empty shell on a phone (navigation bar) and a tablet (navigation rail). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class AppShellScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun shellOnPhone() = capture("shell-phone")

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun shellOnTablet() = capture("shell-tablet")

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun shellOnPhoneDark() = capture("shell-phone-dark", ThemeMode.Dark)

    /** Every destination's selected icon differs in shape, not only colour; Shorts is the one that did not. */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun shellOnPhoneShortsSelected() = capture("shell-phone-shorts", select = "Shorts")

    private fun capture(name: String, mode: ThemeMode = ThemeMode.Light, select: String? = null) {
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = mode)) {
                AlohaApp("a", home = { Placeholder(stringResource(R.string.destination_home)) })
            }
        }
        select?.let { compose.onNodeWithText(it).performClick() }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
