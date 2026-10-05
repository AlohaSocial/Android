// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
class CatchUpScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = object : CatchUpActions {
        override fun onSort(sort: CatchUpSort) {
            asked += "sort:$sort"
        }

        override fun onKind(kind: CatchUpKind) {
            asked += "kind:$kind"
        }

        override fun onPerson(id: String?) {
            asked += "person:$id"
        }

        override fun onCaughtUp() {
            asked += "caught up"
        }
    }

    private object Inert : StatusActions {
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

    private val state: CatchUpUiState
        get() {
            val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
            val news = listOf(StatusSamples.post(), StatusSamples.boost)
            return CatchUpUiState(
                rows = news.map { mapper.map(it, "9") },
                people = peopleOf(news),
                arrived = 2,
                loading = false,
                now = StatusSamples.NOW,
            )
        }

    @Test
    fun `what arrived is sorted and filtered by chips, and the end says it is read`() {
        compose.setContent { AlohaTheme { CatchUpScreen(state, actions, Inert, onBack = {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/catch-up.png")
        compose.onNodeWithText("Most replies").performClick()
        compose.onNodeWithText("Boosts").performClick()
        assertEquals(listOf("sort:Replies", "kind:Boosts"), asked)
    }
}
