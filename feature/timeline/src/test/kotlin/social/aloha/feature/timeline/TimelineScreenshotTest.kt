// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

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
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineSource
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/** The home screen's states, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TimelineScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = RichTextCache()

    private object NoActions : StatusActions, TimelineScreenActions {
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
        override fun onRefresh() = Unit
        override fun onRevealPending() = Unit
        override fun onScrolledToTop() = Unit
        override fun onRestored() = Unit
        override fun onSource(source: TimelineSource) = Unit
        override fun onShowBoosts(show: Boolean) = Unit
        override fun onShowReplies(show: Boolean) = Unit
        override fun onScrolled(rowId: String, offset: Int) = Unit
        override fun onNearEnd() = Unit
        override fun onFillGap(gapId: String) = Unit
        override fun onSwipe(row: StatusRowUi, action: SwipeAction) = Unit
    }

    @Composable
    private fun posts(): List<TimelineItem> {
        val mapper = StatusRowMapper(cache, RichTextColors.fromTheme())
        return listOf(StatusSamples.post(), StatusSamples.boost, StatusSamples.gallery, StatusSamples.poll)
            .mapIndexed { index, status -> TimelineItem.Post(mapper.map(status.copy(id = "row$index"), "1", null)) }
    }

    private fun capture(
        name: String,
        settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        state: @Composable () -> TimelineUiState,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(settings) { TimelineScreen(state(), NoActions, NoActions) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun loaded(items: List<TimelineItem>) =
        TimelineUiState(items = items, loadedOnce = true, sources = AllSources, now = StatusSamples.NOW)

    @Test
    fun loaded() = capture("timeline-loaded") { loaded(posts()) }

    @Test
    fun pendingWhileOffline() = capture("timeline-pending-offline", ThemeSettings(mode = ThemeMode.Dark)) {
        loaded(posts()).copy(pending = 3, trouble = Trouble.Offline)
    }

    @Test
    @Config(fontScale = 2f)
    fun gapLargeFont() = capture("timeline-gap-font200") {
        val posts = posts()
        loaded(listOf(posts[0], TimelineItem.Gap("gap", loading = false), posts[1])).copy(source = TimelineSource.Local)
    }

    @Test
    fun empty() = capture("timeline-empty") { loaded(emptyList()) }

    @Test
    fun firstLoad() = capture("timeline-first-load") { TimelineUiState(sources = AllSources) }
}

/** A server serving every live feed, as most do. */
private val AllSources = listOf(TimelineSource.Home, TimelineSource.Local, TimelineSource.Federated)
