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
import social.aloha.core.model.AccentSource
import social.aloha.core.model.Appearance
import social.aloha.core.model.SensitiveMediaPolicy

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
    fun accounts() = capture("settings-accounts") {
        AccountsContent(
            listOf(
                AccountEntry("1", "Alice", "@alice@cloud.example", null, active = true, needsReauth = false),
                AccountEntry("2", "Alice", "@alice@mastodon.example", null, active = false, needsReauth = true),
            ),
            onMove = { _, _ -> },
            onSignInAgain = {},
            onSignOut = {},
        )
    }

    @Test
    fun media() = capture("settings-media") {
        MediaContent(MediaState(SensitiveMediaPolicy.ShowAll, changeable = true, refused = true), onChoose = {})
    }

    @Test
    fun mediaOnTheWebsite() = capture("settings-media-website") {
        MediaContent(MediaState(SensitiveMediaPolicy.Blur, changeable = false), onChoose = {})
    }

    @Test
    fun appearance() = capture("settings-appearance") {
        AppearanceContent(
            AppearanceState(Appearance(accent = AccentSource.Custom, black = true), serverColour = true),
            onChange = {},
        )
    }

    @Test
    @Config(fontScale = 2f)
    fun appearanceLargeFont() = capture("settings-appearance-font200") {
        AppearanceContent(AppearanceState(Appearance(accent = AccentSource.Custom)), onChange = {})
    }

    @Test
    fun privacy() = capture("settings-privacy") { TextPage("Privacy", onClose = {}) { PrivacyStatement() } }

    @Test
    fun nextcloud() = capture("settings-nextcloud") {
        NextcloudRows(NextcloudUiState(available = true, phase = NextcloudPhase.TimedOut), {}, {}, {})
    }

    @Test
    fun nextcloudWaiting() = capture("settings-nextcloud-waiting") {
        NextcloudRows(NextcloudUiState(available = true, phase = NextcloudPhase.Waiting), {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun nextcloudConnectedLargeFont() = capture("settings-nextcloud-connected-font200") {
        NextcloudRows(NextcloudUiState(available = true, connected = true), {}, {}, {})
    }

    @Test
    fun deleteRefused() = capture("settings-delete-refused") {
        DeleteAccountRows(
            DeleteAccountUiState(
                mode = DeletionMode.InApp,
                handle = "@alice@cloud.example",
                refusal = DeletionRefusal.Server(
                    "type alice@cloud.example to confirm that this is the account to delete",
                ),
            ),
            {},
            {},
            {},
        )
    }

    @Test
    fun deleteOnTheWeb() = capture("settings-delete-web") {
        DeleteAccountRows(
            DeleteAccountUiState(mode = DeletionMode.OnTheWeb, webPage = "https://mastodon.social/settings/delete"),
            {},
            {},
            {},
        )
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun aboutWide() = capture("settings-about-wide") { SectionScreen(AboutSection, onBack = {}) }
}
