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
import social.aloha.core.data.server.ServerAbout
import social.aloha.core.datastore.IntelligenceChoices
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.intelligence.ModelAvailability
import social.aloha.core.model.AccentSource
import social.aloha.core.model.AnnualArchetype
import social.aloha.core.model.AnnualHashtag
import social.aloha.core.model.AnnualMonth
import social.aloha.core.model.AnnualReport
import social.aloha.core.model.AnnualReportData
import social.aloha.core.model.Appearance
import social.aloha.core.model.InstanceDocument
import social.aloha.core.model.InstanceRule
import social.aloha.core.model.PublicDomainBlock
import social.aloha.core.model.ReadingStyle
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
    fun sections() = capture("settings-sections") {
        SettingsScreen(
            listOf(AccountsSection, AppearanceSection, MediaSection, AboutSection),
            onBack = {},
            onSection = {},
            destinations = listOf(
                SettingsDestination("filters", 250, R.string.settings_filters, AlohaIcons.Filtered) {
                },
            ),
        )
    }

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
        MediaContent(MediaState(SensitiveMediaPolicy.ShowAll, changeable = true, refused = true), MediaActions())
    }

    @Test
    fun mediaOnTheWebsite() = capture("settings-media-website") {
        MediaContent(
            MediaState(SensitiveMediaPolicy.Blur, changeable = false, autoplayOnMobileData = false),
            MediaActions(),
        )
    }

    @Test
    fun server() = capture("settings-server") {
        ServerContent(
            ServerState(
                "cloud.example",
                ServerAbout(
                    rules = listOf(InstanceRule("1", "Be kind")),
                    privacyPolicy = InstanceDocument("<p>We keep little.</p>"),
                    domainBlocks = listOf(PublicDomainBlock("spam.example")),
                ),
            ),
        )
    }

    @Test
    fun storage() = capture("settings-storage") { StorageContent(StorageState(bytes = 126_000_000), onClear = {}) }

    @Test
    fun reading() = capture("settings-reading") {
        ReadingContent(ReadingStyle(compact = true, showCounts = false), onChange = {})
    }

    @Test
    fun sound() = capture("settings-sound") { SoundContent(ReadingStyle(), onChange = {}, onNotificationSounds = {}) }

    @Test
    fun intelligence() = capture("settings-intelligence") {
        val choices = IntelligenceChoices(altText = true, rewrite = true)
        val waiting = IntelligenceUi(choices, true, ModelAvailability.NotReady, hasModel = true, modelName = "nano-v3")
        IntelligenceContent(waiting, onChange = {})
    }

    @Test
    fun privacyWithIntelligence() = capture("settings-privacy-intelligence") {
        TextPage("Privacy", onClose = {}) { PrivacyStatement(NOTICE) }
    }

    @Test
    fun year() = capture("settings-year") {
        val report = AnnualReport(
            2026,
            AnnualReportData(
                archetype = AnnualArchetype.Oracle,
                timeSeries = (1..12).map { AnnualMonth(it, statuses = if (it in 8..10) it * 2 else 0) },
                topHashtags = listOf(AnnualHashtag("aloha", 3), AnnualHashtag("surf", 1)),
            ),
        )
        YearScreen(
            YearState(
                loading = false,
                reports = listOf(report, AnnualReport(2025)),
                shown = 2026,
                posts = mapOf(2026 to listOf(TopPost(TopKind.Favourites, "s1", "Surf\u2019s up at dawn"))),
            ),
            onOpenPost = {},
            onYear = {},
            onRetry = {},
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "+en-rXA")
    fun mediaPseudolocale() = capture("settings-media-en-xa") {
        MediaContent(MediaState(SensitiveMediaPolicy.Blur, changeable = true), MediaActions())
    }

    @Test
    fun appLock() = capture("settings-privacy-lock") {
        PrivacyContent(LockState(enabled = true, timeoutSeconds = 300), secure = true, onEnabled = {}, onTimeout = {})
    }

    @Test
    fun privacyWithoutScreenLock() = capture("settings-privacy-no-screen-lock") {
        PrivacyContent(LockState(), secure = false, onEnabled = {}, onTimeout = {})
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
    fun privacy() = capture("settings-privacy") {
        TextPage("Privacy", onClose = {}) { PrivacyStatement(intelligence = null) }
    }

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

    private companion object {
        // a build's own paragraph, as the generic build words it
        const val NOTICE = "Drafting alt text runs on this device with open software, only once you turn it on " +
            "and tap it: your pictures are not sent anywhere, and the software reports to no one."
    }
}
