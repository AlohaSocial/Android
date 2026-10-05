// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowUi

/** A timeline's actions that do nothing, for screens drawn to be looked at. */
internal object NoTimelineActions : StatusActions, TimelineScreenActions {
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
    override fun onFillGap(gapId: String, fromBelow: Boolean) = Unit
    override fun onExpandBoosts(key: String) = Unit
    override fun onSwipe(row: StatusRowUi, action: SwipeAction) = Unit
}
