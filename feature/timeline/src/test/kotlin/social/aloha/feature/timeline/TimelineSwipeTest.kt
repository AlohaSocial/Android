// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.ReadingStyle
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineSource
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/** What the hand is told as a post is swiped: one tick, as the swipe starts to count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TimelineSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()
    private val ticks = mutableListOf<HapticFeedbackType>()

    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            ticks += hapticFeedbackType
        }
    }

    private val actions = object : StatusActions, TimelineScreenActions {
        override fun onSwipe(row: StatusRowUi, action: SwipeAction) {
            done += "${action.name.lowercase()} ${row.statusId}"
        }

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
    }

    @Composable
    private fun state(): TimelineUiState {
        val mapper = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme())
        val items = (0..1).map { TimelineItem.Post(mapper.map(StatusSamples.post().copy(id = "s$it"), "1", null)) }
        return TimelineUiState(items = items, loadedOnce = true, now = StatusSamples.NOW)
    }

    private fun show(haptics: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(
                LocalHapticFeedback provides recorder,
                LocalReadingStyle provides ReadingStyle(haptics = haptics),
            ) {
                AlohaTheme { TimelineScreen(state(), actions, actions, onCompose = {}) }
            }
        }
    }

    private val cards get() = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(TIMELINE_LIST)))

    @Test
    fun `a swipe ticks once, as it crosses the point of acting, not again as it acts`() {
        show()
        cards[0].performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf("favourite s0"), done)
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), ticks)
        cards[1].performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf("favourite s0", "boost s1"), done)
        assertEquals(2, ticks.size)
    }

    @Test
    fun `three tenths of the width counts, a fifth does not`() {
        show()
        cards[0].performTouchInput { swipeRight(startX = centerX, endX = centerX + width * SHORT) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), done)
        assertEquals(emptyList<HapticFeedbackType>(), ticks)
        cards[0].performTouchInput { swipeRight(startX = centerX, endX = centerX + width * ENOUGH) }
        compose.waitForIdle()
        assertEquals(listOf("favourite s0"), done)
    }

    @Test
    fun `with haptics off the swipe still acts, in silence`() {
        show(haptics = false)
        cards[0].performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf("favourite s0"), done)
        assertEquals(emptyList<HapticFeedbackType>(), ticks)
    }

    private companion object {
        const val SHORT = 0.2f
        const val ENOUGH = 0.35f
    }
}
