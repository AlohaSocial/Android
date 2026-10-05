// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.ActAs
import social.aloha.core.ui.PostAct
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowUi

/**
 * A post read from another server, whose ids there mean nothing on the reader's own: it opens by its
 * address, which the reader's server finds, and is replied to, boosted, favourited and bookmarked as the
 * reader through [actAs], which finds it there first. A poll is voted in, and a post reacted to, where it
 * opens; its menu shares it and opens it in the browser, nothing that asks the reader's server about it.
 * Its author opens by their handle.
 */
internal class Elsewhere(
    private val base: StatusActions,
    private val actAs: ActAs?,
    private val navigation: () -> StatusNavigation,
    private val urlOf: (statusId: String) -> String?,
) : StatusActions by base {
    override val quotes: Boolean get() = false

    override val menu: Set<StatusMenuItem> get() = MENU

    override fun onOpen(statusId: String) {
        urlOf(statusId)?.let(navigation()::openWeb)
    }

    override fun onVote(row: StatusRowUi, choices: List<Int>) = onOpen(row.statusId)

    override fun onReact(row: StatusRowUi, name: String, add: Boolean) = onOpen(row.statusId)

    override fun onReply(row: StatusRowUi) = act(row, PostAct.Reply)

    override fun onBoost(row: StatusRowUi) = act(row, PostAct.Boost())

    override fun onFavourite(row: StatusRowUi) = act(row, PostAct.Favourite)

    override fun onBookmark(row: StatusRowUi) = act(row, PostAct.Bookmark)

    override fun onProfile(accountId: String) =
        navigation().openProfile(null, accountId.removePrefix(TimelineSource.Remote.ID_PREFIX))

    private fun act(row: StatusRowUi, act: PostAct) {
        val url = row.url ?: return
        val reader = actAs?.current ?: return
        actAs.act(reader, url, act)
    }
}

private val MENU = setOf(StatusMenuItem.Share, StatusMenuItem.CopyLink, StatusMenuItem.OpenInBrowser)
