// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
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

/** The timeline read from a hardware keyboard: what each key does, and to which post. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TimelineKeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()

    private val actions = object : StatusActions, TimelineScreenActions {
        override fun onOpen(statusId: String) {
            done += "open $statusId"
        }

        override fun onFavourite(row: StatusRowUi) {
            done += "favourite ${row.statusId}"
        }

        override fun onBoost(row: StatusRowUi) {
            done += "boost ${row.statusId}"
        }

        override fun onReply(row: StatusRowUi) {
            done += "reply ${row.statusId}"
        }

        override fun onProfile(accountId: String) = Unit
        override fun onLink(target: RichLinkTarget) = Unit
        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
        override fun onRefresh() = Unit
        override fun onRevealPending() = Unit

        override fun onCaughtUp() = Unit
        override fun onScrolledToTop() = Unit
        override fun onRestored() = Unit
        override fun onSource(source: TimelineSource) = Unit
        override fun onShowBoosts(show: Boolean) = Unit
        override fun onShowReplies(show: Boolean) = Unit
        override fun onGrid(grid: Boolean) = Unit
        override fun onScrolled(rowId: String, offset: Int) = Unit
        override fun onNearEnd() = Unit
        override fun onFillGap(gapId: String) = Unit
        override fun onSwipe(row: StatusRowUi, action: SwipeAction) = Unit
    }

    @Composable
    private fun state(): TimelineUiState {
        val mapper = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme())
        val items = (0..2).map { TimelineItem.Post(mapper.map(StatusSamples.post().copy(id = "s$it"), "1", null)) }
        return TimelineUiState(items = items, loadedOnce = true, now = StatusSamples.NOW)
    }

    private fun show() {
        compose.setContent {
            AlohaTheme { TimelineScreen(state(), actions, actions, onCompose = { done += "compose" }) }
        }
    }

    private val list get() = compose.onNodeWithTag(TIMELINE_LIST)

    private val cards get() = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(TIMELINE_LIST)))

    @Test
    fun `J selects posts in turn, and the other keys act on the one selected`() {
        show()
        list.requestFocus()
        list.performKeyInput {
            pressKey(Key.J)
            pressKey(Key.J)
            pressKey(Key.F)
            pressKey(Key.L)
            pressKey(Key.B)
            pressKey(Key.R)
            pressKey(Key.K)
            pressKey(Key.O)
            pressKey(Key.N)
        }
        compose.waitForIdle()
        assertEquals(
            listOf("favourite s1", "favourite s1", "boost s1", "reply s1", "open s0", "compose"),
            done,
        )
    }

    @Test
    fun `the post selected is the one read as selected`() {
        show()
        list.requestFocus()
        list.performKeyInput { pressKey(Key.J) }
        compose.waitForIdle()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        cards[0].assert(isSelected())
    }

    @Test
    fun `Enter opens the post selected, not the one whose card had focus`() {
        show()
        cards[0].requestFocus()
        cards[0].performKeyInput { pressKey(Key.J) }
        // moving took the focus back to the list
        list.performKeyInput {
            pressKey(Key.J)
            pressKey(Key.Enter)
        }
        compose.waitForIdle()
        assertEquals(listOf("open s1"), done)
    }

    @Test
    fun `nothing selected, a post's key does nothing`() {
        show()
        list.requestFocus()
        list.performKeyInput { pressKey(Key.F) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), done)
    }
}
