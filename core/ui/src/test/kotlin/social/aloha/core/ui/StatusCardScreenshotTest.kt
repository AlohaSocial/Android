// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ContrastPreference
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Status

/**
 * Every state of a row, in light, dark, high contrast, black, 200 % font and right to left, each also
 * run through the Accessibility Test Framework checks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatusCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = RichTextCache()

    private object NoActions : StatusActions {
        override fun onOpen(statusId: String) = Unit
        override fun onProfile(accountId: String) = Unit
        override fun onLink(target: RichLinkTarget) = Unit
        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onReply(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
    }

    @Composable
    private fun Rows(
        statuses: List<Status>,
        policy: SensitiveMediaPolicy = SensitiveMediaPolicy.Blur,
        filtered: Boolean = false,
    ) {
        val mapper = StatusRowMapper(cache, RichTextColors.fromTheme())
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                statuses.forEach { status ->
                    StatusCard(
                        mapper.map(
                            status,
                            viewerAccountId = "1",
                            filterWarning = if (filtered) listOf("Spoilers") else null,
                        ),
                        StatusSamples.NOW,
                        policy,
                        NoActions,
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    private fun capture(
        name: String,
        settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        direction: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) { AlohaTheme(settings) { content() } }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val everyday = listOf(StatusSamples.post(), StatusSamples.boost, StatusSamples.reply, StatusSamples.direct)

    @Test
    fun everyday() = capture("status-everyday") { Rows(everyday) }

    @Test
    fun everydayDark() = capture("status-everyday-dark", ThemeSettings(mode = ThemeMode.Dark)) { Rows(everyday) }

    @Test
    fun everydayHighContrast() = capture(
        "status-everyday-high-contrast",
        ThemeSettings(mode = ThemeMode.Light, contrast = ContrastPreference.High),
    ) {
        Rows(everyday)
    }

    @Test
    fun everydayBlack() = capture("status-everyday-black", ThemeSettings(mode = ThemeMode.Dark, black = true)) {
        Rows(everyday)
    }

    @Test
    @Config(fontScale = 2f)
    fun everydayLargeFont() = capture("status-everyday-font200") { Rows(everyday.take(2)) }

    @Test
    @Config(qualifiers = "+ar-rXB-ldrtl")
    fun everydayRightToLeft() = capture("status-everyday-rtl", direction = LayoutDirection.Rtl) { Rows(everyday) }

    @Test
    fun warnings() = capture("status-warnings") {
        Rows(listOf(StatusSamples.spoiler, StatusSamples.sensitive, StatusSamples.selfReply))
    }

    @Test
    fun hiddenMedia() = capture("status-media-hidden") {
        Rows(listOf(StatusSamples.sensitive), policy = SensitiveMediaPolicy.HideAll)
    }

    @Test
    fun filtered() = capture("status-filtered") { Rows(listOf(StatusSamples.post()), filtered = true) }

    @Test
    fun gallery() = capture("status-gallery") {
        Rows(listOf(StatusSamples.gallery), policy = SensitiveMediaPolicy.ShowAll)
    }

    @Test
    fun attachments() = capture("status-attachments") {
        Rows(listOf(StatusSamples.poll, StatusSamples.pollResults, StatusSamples.linked, StatusSamples.quoting))
    }
}
