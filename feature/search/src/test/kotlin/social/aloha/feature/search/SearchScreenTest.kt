// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.search

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Tag
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
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = SearchActions(
        onQuery = { asked += "query:$it" },
        onSubmit = { asked += "submit:$it" },
        onClearRecent = { asked += "clear" },
        onBack = { asked += "back" },
    )

    private val rows = object : StatusActions {
        override fun onOpen(statusId: String) {
            asked += "open:$statusId"
        }

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

    private val found = SearchUiState(
        query = "beach",
        accounts = listOf(SearchPerson(mapper.author(StatusSamples.alice), emptyList())),
        hashtags = listOf(Tag("beach")),
        posts = listOf(mapper.map(StatusSamples.post(), viewerAccountId = "2")),
        searched = true,
    )

    @Test
    fun `the recent searches search again on a tap, and clear`() {
        compose.setContent {
            AlohaTheme { SearchScreen(SearchUiState(recent = listOf("surf", "#beach")), actions, rows) }
        }
        compose.onNodeWithText("surf").performClick()
        compose.onNodeWithText("Clear").performClick()
        assertEquals(listOf("submit:surf", "clear"), asked)
    }

    @Test
    fun `an account found opens its profile, and a hashtag its timeline`() {
        compose.setContent { AlohaTheme { SearchScreen(found, actions, rows) } }
        compose.onNodeWithText("@alice", substring = true).performClick()
        compose.onNodeWithText("#beach").performClick()
        assertEquals(listOf("profile:1", "link:${RichLinkTarget.Hashtag("beach")}"), asked)
    }

    @Test
    fun results() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme { SearchScreen(found, actions, rows, now = StatusSamples.NOW, initiallyEditing = false) }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/search-results.png")
    }

    @Test
    @Config(fontScale = 2f)
    fun resultsLargeFont() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme { SearchScreen(found, actions, rows, now = StatusSamples.NOW, initiallyEditing = false) }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/search-results-font200.png")
    }

    @Test
    fun recent() {
        compose.setContent {
            AlohaTheme { SearchScreen(SearchUiState(recent = listOf("surf", "#beach")), actions, rows) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/search-recent.png")
    }
}
