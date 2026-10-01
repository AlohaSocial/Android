// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import android.app.Application
import androidx.compose.material3.Surface
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
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.Story
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StoriesRailScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val reels = listOf(
        StoryReel(StatusSamples.alice, listOf(Story("1", seen = true)), own = true),
        StoryReel(StatusSamples.bob, listOf(Story("2", seen = false)), own = false),
        StoryReel(
            StatusSamples.bob.copy(id = "3", displayName = "Someone with a very long name"),
            listOf(Story("3", seen = true)),
            own = false,
        ),
    )

    @Test
    fun rail() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) {
                Surface { StoriesRail(reels, onOpen = {}, onNewStory = {}) }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/stories-rail.png")
    }
}
