// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
import social.aloha.core.model.FeedMode
import social.aloha.core.model.ModeChoices
import social.aloha.core.model.SwipeAction
import social.aloha.core.ui.readingColumn

/** The timeline's settings rows at each width and at 200 % font, each also run through the ATF checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TimelineSettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private object NoActions : TimelineSettingsActions {
        override fun onShowBoosts(show: Boolean) = Unit
        override fun onShowReplies(show: Boolean) = Unit
        override fun onSwipeTowardsEnd(action: SwipeAction) = Unit
        override fun onSwipeTowardsStart(action: SwipeAction) = Unit
    }

    private fun capture(name: String) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.readingColumn().verticalScroll(rememberScrollState())) {
                        TimelineSettingsContent(
                            TimelineSettingsState(showReplies = false, swipeTowardsStart = SwipeAction.Bookmark),
                            NoActions,
                        )
                    }
                }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun phone() = capture("timeline-settings")

    @Test
    @Config(fontScale = 2f)
    fun phoneLargeFont() = capture("timeline-settings-font200")

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun wide() = capture("timeline-settings-wide")

    @Test
    fun modes() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.readingColumn().verticalScroll(rememberScrollState())) {
                        ModesSettingsContent(
                            ModesSettingsState(
                                ModeChoices().turned(FeedMode.Audio, on = true, slot = 1),
                                listOf(FeedMode.News, FeedMode.Audio),
                            ),
                            onTurned = { _, _ -> },
                            onSlot = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/modes-settings.png")
    }

    @Test
    fun modesOffer() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ModesOfferContent(
                        ModesSettingsState(ModeChoices(), listOf(FeedMode.News, FeedMode.Audio)),
                        onTurned = { _, _ -> },
                        onSlot = { _, _ -> },
                        onDone = {},
                    )
                }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/modes-offer.png")
    }
}
