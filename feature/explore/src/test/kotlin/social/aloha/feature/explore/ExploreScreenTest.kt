// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.Trouble
import social.aloha.core.data.explore.People
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
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

        override fun onFollowFailedShown() {
            asked += "followFailedShown"
        }

        override fun onWeb(url: String) {
            asked += "web:$url"
        }

        override fun onOpenPack(pack: StarterPack) {
            asked += "open:${pack.slug}"
        }

        override fun onClosePack() {
            asked += "close"
        }

        override fun onRetryPack() {
            asked += "retryPack"
        }

        override fun onFollowMember(member: Account) {
            asked += "member:${member.id}"
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

    private val surfers = StarterPack(
        "surfers",
        "Surfers",
        "People who surf",
        size = 12,
        curator = "kai@waves.example",
        url = "https://waves.example/c/1",
    )

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

    @Test
    fun `a starter pack read in says who curated it and links to where it lives`() {
        val pack =
            StarterPack("surf", "Surf", size = 3, curator = "kai@waves.example", url = "https://waves.example/c/1")
        show(ExploreUiState(tab = ExploreTab.People, people = Load.Loaded(People(packs = listOf(pack)))))
        compose.onNodeWithText("By @kai@waves.example · 3 accounts").assertExists()
        compose.onNodeWithText("Follow all").performClick()
        compose.onNodeWithText("See it on its server").performClick()
        assertEquals(listOf("web:https://waves.example/c/1"), asked)
    }

    @Test
    fun `a starter pack that could not be followed says so`() {
        show(ExploreUiState(tab = ExploreTab.People, people = Load.Loaded(people), followFailed = true))
        compose.onNodeWithText("Couldn’t follow anyone in that pack. Try again later.").assertExists()
        // cleared once it was said, not before
        assertEquals(emptyList<String>(), asked)
        compose.mainClock.advanceTimeBy(SNACKBAR_MILLIS)
        compose.waitForIdle()
        assertEquals(listOf("followFailedShown"), asked)
    }

    @Test
    fun `a starter pack opens when tapped`() {
        show(ExploreUiState(tab = ExploreTab.People, people = Load.Loaded(people)))
        compose.onNodeWithText("Surfers").performClick()
        assertEquals(listOf("open:surfers"), asked)
    }

    private val kai = Account("7", "kai", "kai@waves.example", displayName = "Kai")

    // Alice is followed, Kai is not, Bob is the reader, and one handle is not reachable yet
    private val opened = ExploreUiState(
        tab = ExploreTab.People,
        people = Load.Loaded(people),
        viewer = "2",
        following = mapOf("1" to true, "7" to false),
        sheet = PackSheet(
            surfers,
            Load.Loaded(
                surfers.copy(
                    accounts = listOf(StatusSamples.alice, kai, StatusSamples.bob),
                    unresolved = listOf("lani@gone.example"),
                ),
            ),
        ),
    )

    @Test
    fun `an open starter pack marks whom the reader follows and follows the others`() {
        show(opened)
        compose.onNodeWithText("Following").assertExists()
        compose.onNodeWithText("Not reachable yet").assertExists()
        compose.onNodeWithText("lani@gone.example").assertExists()
        compose.onNodeWithContentDescription("Follow Kai").performClick()
        compose.onNodeWithText("Follow the rest").performClick()
        compose.onNodeWithText("See it on its server").performClick()
        assertEquals(listOf("member:7", "follow:surfers", "web:https://waves.example/c/1"), asked)
    }

    @Test
    fun `an open starter pack whose members could not be read offers to try again`() {
        show(opened.copy(sheet = PackSheet(surfers, Load.Failed(Trouble.Offline))))
        compose.onNode(hasText("Surfers") and isHeading()).assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retryPack"), asked)
    }

    @Test
    fun `a starter pack the reader follows everyone in says so`() {
        val all = opened.following + ("7" to true)
        val sheet = opened.sheet?.copy(members = Load.Loaded(surfers.copy(accounts = listOf(StatusSamples.alice, kai))))
        show(opened.copy(following = all, sheet = sheet))
        compose.onNodeWithText("You follow everyone in it").assertExists()
        // the pack's row behind the sheet still offers it
        compose.onAllNodesWithText("Follow all").assertCountEquals(1)
    }

    @Test
    fun packSheet() = captureSheet("explore-pack")

    @Test
    @Config(fontScale = 2f)
    fun packSheetLargeFont() = captureSheet("explore-pack-font200")

    // a sheet is a window of its own, so its root is checked and the whole screen captured
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureSheet(name: String) {
        compose.enableAccessibilityChecks()
        show(opened)
        compose.onAllNodes(isRoot()).onLast().tryPerformAccessibilityChecks()
        captureScreenRoboImage("src/test/screenshots/$name.png")
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

private const val SNACKBAR_MILLIS = 10_000L
