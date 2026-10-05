// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.AccountList
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.TimelineSource

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class EditFeedsTest {
    @get:Rule
    val compose = createComposeRule()

    private val following = PinnedFeed(PinnedFeed.Kind.Following)
    private val local = PinnedFeed(PinnedFeed.Kind.ThisServer)
    private val surf =
        PinnedFeed(PinnedFeed.Kind.Hashtag(TimelineSource.Hashtag("surf")), name = "Surf report", icon = "news")
    private val state = EditFeedsState(
        feeds = listOf(following, local, surf),
        lists = listOf(AccountList("7", "Friends")),
        tags = listOf("surf", "hawaii"),
        federatedFeed = false,
    )
    private var ordered: List<PinnedFeed>? = null

    private val actions = object : EditFeedsActions {
        override fun onOrder(feeds: List<PinnedFeed>) {
            ordered = feeds
        }

        override fun onRemove(feed: PinnedFeed) = Unit
        override fun onChange(old: PinnedFeed, new: PinnedFeed) = Unit
        override fun onAdd(feed: PinnedFeed) = Unit
        override fun onRestore(feed: PinnedFeed, at: Int) = Unit
    }

    @Test
    fun `the Add menu offers what the server serves and is not pinned yet, by group`() {
        val offered = state.addable.associate { (group, feeds) -> group to feeds.map { it.id } }
        assertNull(offered[R.string.feeds_add_live])
        assertEquals(listOf("notified", "bookmarks", "favourites"), offered[R.string.feeds_add_yours])
        assertEquals(listOf("list:7"), offered[R.string.feeds_add_lists])
        assertEquals(listOf("tag:hawaii"), offered[R.string.feeds_add_hashtags])
    }

    @Test
    fun `a screen reader moves a feed with the row's actions, and the order is kept`() {
        compose.setContent { AlohaTheme { EditFeedsScreen(state, actions, onBack = {}) } }
        val row = compose.onNodeWithText("Following").fetchSemanticsNode()
        val down = row.config[SemanticsActions.CustomActions].single { it.label == "Move down" }
        compose.runOnIdle { down.action() }
        assertEquals(listOf(local, following, surf), ordered)
    }

    @Test
    fun `a feed swiped away and undone is back where it was, and stays`() {
        var feeds by mutableStateOf(state.feeds)
        var removals = 0
        val stored = object : EditFeedsActions by actions {
            override fun onRemove(feed: PinnedFeed) {
                removals++
                feeds = feeds - feed
            }

            override fun onRestore(feed: PinnedFeed, at: Int) {
                feeds = feeds.toMutableList().apply { add(at, feed) }
            }
        }
        compose.setContent { AlohaTheme { EditFeedsScreen(state.copy(feeds = feeds), stored, onBack = {}) } }
        compose.onNodeWithText("This server").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("This server").assertExists()
        assertEquals(listOf(following, local, surf), feeds)
        assertEquals(1, removals)
    }

    @Test
    fun `the feeds show once they arrive, and Following is the one that cannot be removed`() {
        var shown by mutableStateOf(EditFeedsState())
        compose.setContent { AlohaTheme { EditFeedsScreen(shown, actions, onBack = {}) } }
        shown = state
        compose.waitForIdle()
        val following = compose.onNodeWithText("Following").fetchSemanticsNode()
        assertTrue(following.config[SemanticsActions.CustomActions].none { it.label == "Remove" })
        val local = compose.onNodeWithText("This server").fetchSemanticsNode()
        assertTrue(local.config[SemanticsActions.CustomActions].any { it.label == "Remove" })
    }

    @Test
    fun `a server is taken from an address, a bare name or a handle, and nothing else`() {
        assertEquals("mastodon.social", domainOf("https://Mastodon.social/explore"))
        assertEquals("mastodon.social", domainOf(" mastodon.social "))
        assertEquals("mastodon.social", domainOf("@alice@mastodon.social"))
        assertNull(domainOf("not a server"))
        assertNull(domainOf("localhost"))
    }

    @Test
    fun `typed tags split on spaces and commas, with a leading hash dropped`() {
        assertEquals(listOf("waves", "hawaii", "oahu"), TagQuery.words("#waves, hawaii  oahu"))
        assertEquals(
            TimelineSource.Hashtag("surf", any = listOf("waves"), localOnly = true),
            TagQuery("#surf", any = "waves", localOnly = true).source(),
        )
    }

    private fun capture(name: String, settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light)) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(settings) { EditFeedsScreen(state, actions, onBack = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun editFeeds() = capture("edit-feeds")

    @Test
    fun editFeedsDark() = capture("edit-feeds-dark", ThemeSettings(mode = ThemeMode.Dark))

    @Test
    @Config(fontScale = 2f)
    fun editFeedsLargeFont() = capture("edit-feeds-font200")
}
