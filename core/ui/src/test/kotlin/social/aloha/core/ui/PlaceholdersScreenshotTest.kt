// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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

/** The loading skeleton above an empty state, in light, dark, black, 200 % font and right to left. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlaceholdersScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(
        name: String,
        settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AlohaTheme(settings) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column {
                            Skeleton(rows = 2)
                            EmptyState(
                                "Nothing here yet",
                                Modifier.height(EMPTY_HEIGHT),
                                body = "Follow people and their posts show up here.",
                                action = EmptyAction("See what’s happening") {},
                            )
                        }
                    }
                }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("placeholders")

    @Test
    fun dark() = capture("placeholders-dark", ThemeSettings(mode = ThemeMode.Dark))

    @Test
    fun black() = capture("placeholders-black", ThemeSettings(mode = ThemeMode.Dark, black = true))

    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("placeholders-font200")

    @Test
    fun rightToLeft() = capture("placeholders-rtl", direction = LayoutDirection.Rtl)

    private companion object {
        val EMPTY_HEIGHT = 360.dp
    }
}
