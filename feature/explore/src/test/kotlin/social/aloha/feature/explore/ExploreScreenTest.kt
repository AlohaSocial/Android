// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.explore.People
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.StarterPack
import social.aloha.core.model.Suggestion
import social.aloha.core.model.Tag
import social.aloha.core.model.TagHistory
import social.aloha.core.network.endpoints.DirectoryOrder
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ExploreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = object : ExploreActions {
        override fun onTab(tab: ExploreTab) {
            asked += "tab:$tab"
        }

        override fun onRetry() {
            asked += "retry"
        }

        override fun onPeriod(period: String) {
            asked += "period:$period"
        }

        override fun onOrder(order: DirectoryOrder) {
            asked += "order:$order"
        }

        override fun onMoreDirectory() {
            asked += "more"
        }

        override fun onDismiss(accountId: String) {
            asked += "dismiss:$accountId"
        }

        override fun onFollowAll(slug: String) {
            asked += "follow:$slug"
        }

        override fun onWeb(url: String) {
            asked += "web:$url"
        }
    }

    private val rows = object : StatusActions {
        override fun onOpen(statusId: String) = Unit

        override fun onProfile(accountId: String) {
            asked += "profile:$accountId"
        }

        override fun onLink(target: RichLinkTarget) {
            asked += "link:$target"
        }

        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onReply(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
    }

    private val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))

    private val people = People(
        suggestions = listOf(Suggestion(StatusSamples.bob)),
        packs = listOf(StarterPack("surfers", "Surfers", "People who surf", size = 12)),
        popular = listOf(StatusSamples.alice),
    )

    private fun show(state: ExploreUiState) {
        compose.setContent { AlohaTheme { Surface { ExploreScreen(state, mapper, actions, rows) } } }
    }

    @Test
    fun `a suggestion is dismissed, and a starter pack is followed only once asked`() {
        show(ExploreUiState(tab = ExploreTab.People, people = Load.Loaded(people)))
        compose.onNodeWithContentDescription("Don’t suggest Bob").performClick()
        compose.onNodeWithText("Follow all").performClick()
        compose.onNodeWithText("Follow everyone in Surfers?").assertExists()
        compose.onNodeWithText("You will follow all 12 of its accounts.").assertExists()
        // nothing is followed before the dialog is answered
        assertEquals(listOf("dismiss:2"), asked)
        compose.onAllNodesWithText("Follow all").onLast().performClick()
        assertEquals(listOf("dismiss:2", "follow:surfers"), asked)
    }

    // one bucket, as Nextcloud Social keeps, a week, as Mastodon does, and none
    private val trending = listOf(
        Tag("surf", history = listOf(TagHistory("1", "12", "0"))),
        Tag("waves", history = listOf(30, 22, 25, 9, 14, 4, 6).map { TagHistory("$it", "$it", "3") }),
        Tag("reef"),
    )

    @Test
    fun hashtags() {
        compose.enableAccessibilityChecks()
        val tags = trending
        show(ExploreUiState(tab = ExploreTab.Hashtags, hashtags = Load.Loaded(tags), periods = true))
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("Used 12 times").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/explore-hashtags.png")
        compose.onNodeWithText("#surf").performClick()
        assertEquals(listOf("link:${RichLinkTarget.Hashtag("surf")}"), asked)
    }

    @Test
    @Config(fontScale = 2f)
    fun hashtagsLargeFont() {
        compose.enableAccessibilityChecks()
        val tags = trending
        show(ExploreUiState(tab = ExploreTab.Hashtags, hashtags = Load.Loaded(tags), periods = true))
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("Used 12 times").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/explore-hashtags-font200.png")
        compose.onNodeWithText("#surf").performClick()
        assertEquals(listOf("link:${RichLinkTarget.Hashtag("surf")}"), asked)
    }

    @Test
    fun people() {
        compose.enableAccessibilityChecks()
        show(ExploreUiState(tab = ExploreTab.People, people = Load.Loaded(people)))
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/explore-people.png")
    }

    @Test
    fun `the directory says who is in it, and pages on`() {
        show(
            ExploreUiState(
                tab = ExploreTab.Directory,
                directory = Directory(accounts = listOf(StatusSamples.alice, StatusSamples.bob)),
            ),
        )
        compose.onNodeWithText("Only accounts on this server that chose to be listed appear here.").assertExists()
        compose.onNodeWithText("Show more").performClick()
        compose.onNodeWithText("New here").performClick()
        assertEquals(listOf("more", "order:New"), asked)
        compose.onRoot().captureRoboImage("src/test/screenshots/explore-directory.png")
    }
}
