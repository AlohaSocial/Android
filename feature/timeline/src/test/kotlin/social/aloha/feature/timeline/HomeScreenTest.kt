// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.TimelineSource
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

/** Home's pinned feeds under one bar: the switcher, the pages and a feed's first-visit explanation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val feeds = listOf(
        PinnedFeed(PinnedFeed.Kind.Following),
        PinnedFeed(PinnedFeed.Kind.ThisServer),
        PinnedFeed(PinnedFeed.Kind.Hashtag(TimelineSource.Hashtag("surf")), name = "Surf report", icon = "news"),
    )
    private val asked = mutableListOf<String>()

    private val actions = object : HomeActions {
        override fun onListed() {
            asked += "listed"
        }

        override fun onShowBoosts(show: Boolean) = Unit
        override fun onShowReplies(show: Boolean) = Unit
        override fun onExplained(feed: PinnedFeed) {
            asked += "explained ${feed.id}"
        }
    }

    @Composable
    private fun Home(state: HomeFeedsState) {
        val mapper = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme())
        val rows = listOf(StatusSamples.post(), StatusSamples.boost)
            .mapIndexed { index, status -> TimelineItem.Post(mapper.map(status.copy(id = "row$index"), "1", null)) }
        HomeScreen(state, actions, HomeChrome(onEditFeeds = {})) { _, list: LazyListState, banner ->
            TimelineScreen(
                TimelineUiState(items = rows, loadedOnce = true, now = StatusSamples.NOW),
                NoTimelineActions,
                NoTimelineActions,
                listState = list,
                header = banner,
                bar = false,
            )
        }
    }

    private fun capture(
        name: String,
        state: HomeFeedsState = HomeFeedsState(feeds, unread = setOf(feeds[2].id)),
        settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) { AlohaTheme(settings) { Home(state) } }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun home() = capture("home-feeds")

    @Test
    fun homeDark() = capture("home-feeds-dark", settings = ThemeSettings(mode = ThemeMode.Dark))

    @Test
    @Config(fontScale = 2f)
    fun homeLargeFont() = capture("home-feeds-font200")

    @Test
    fun homeRightToLeft() = capture("home-feeds-rtl", direction = LayoutDirection.Rtl)

    @Test
    fun aFeedExplainsItselfOnItsFirstVisit() = capture(
        "home-feed-banner",
        HomeFeedsState(listOf(feeds[1], feeds[0])),
    )

    @Test
    fun `a feed added while Home is open joins its pages`() {
        var state by mutableStateOf(HomeFeedsState(feeds.take(2)))
        compose.setContent { AlohaTheme { Home(state) } }
        state = HomeFeedsState(feeds)
        compose.waitForIdle()
        compose.onNodeWithText("Following").performClick()
        compose.onNodeWithText("Surf report").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Surf report").assertExists()
    }

    @Test
    fun `the feed name lists the feeds, with news marked, and a pick moves there`() {
        compose.setContent { AlohaTheme { Home(HomeFeedsState(feeds, unread = setOf(feeds[2].id))) } }
        compose.onNodeWithText("Following").performClick()
        assertEquals(listOf("listed"), asked)
        compose.onNodeWithText("Surf report").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Surf report").assertExists()
        compose.onNodeWithText("Following").assertDoesNotExist()
    }

    @Test
    fun `where the reader chose it, a tap on the feed name moves to the next feed`() {
        compose.setContent { AlohaTheme { Home(HomeFeedsState(feeds, titleNext = true)) } }
        compose.onNodeWithText("Following").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("This server").assertExists()
    }
}
