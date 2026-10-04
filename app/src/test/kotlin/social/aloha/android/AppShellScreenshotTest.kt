// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.feature.timeline.TimelineFeed

/** The empty shell on a phone (navigation bar) and a tablet (navigation rail), and the accounts sheet. */
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

    /** The sheet is a window of its own, which only a capture of the whole screen includes. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun accounts() {
        val accounts = listOf(
            SwitcherAccount("a", "Alice Example", "@alice@cloud.example", null, active = true, needsReauth = false),
            SwitcherAccount("b", "Alice at work", "@alice@mastodon.example", null, active = false, needsReauth = true),
        )
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) { AccountSheet(accounts, {}, AccountLinks(), {}, {}, {}) }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/shell-accounts.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun locked() {
        var asked = 0
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) { LockScreen(onUnlock = { asked++ }) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/locked.png")
        // the prompt comes at once, and again from the button
        compose.onNodeWithText("Unlock").performClick()
        org.junit.Assert.assertEquals(2, asked)
    }

    private fun capture(name: String, mode: ThemeMode = ThemeMode.Light, select: String? = null) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = mode)) {
                AlohaApp("a", "1", timeline = { feed, _, _, _ -> Placeholder(feed.label()) }, nowPlaying = {})
            }
        }
        select?.let { compose.onNodeWithText(it).performClick() }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

/** What the stand-in for a timeline says: which of them it stands for. */
@Composable
private fun TimelineFeed.label(): String = when (this) {
    is TimelineFeed.Mode -> mode.name
    else -> stringResource(R.string.destination_home)
}
