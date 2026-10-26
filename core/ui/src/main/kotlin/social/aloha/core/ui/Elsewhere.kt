// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

/**
 * A post read from another server, whose ids there mean nothing on the reader's own: it opens by its
 * address, which the reader's server finds, and is replied to, boosted, favourited and bookmarked through
 * [actAs], which finds it there first, as the account [actingAs] names, the reader's unless said. A poll
 * is voted in, and a post reacted to, where it opens; its menu shares it and opens it in the browser,
 * nothing that asks the reader's server about it. Its author opens by their handle.
 */
public class Elsewhere(
    private val base: StatusActions,
    private val actAs: ActAs?,
    private val navigation: () -> StatusNavigation,
    private val urlOf: (statusId: String) -> String?,
    private val actingAs: (StatusRowUi) -> String? = { actAs?.current },
) : StatusActions by base {
    override val quotes: Boolean get() = false

    override val menu: Set<StatusMenuItem> get() = MENU

    override fun menuFor(row: StatusRowUi): Set<StatusMenuItem> = MENU

    override fun onOpen(statusId: String) {
        urlOf(statusId)?.let(navigation()::openWeb)
    }

    override fun onVote(row: StatusRowUi, choices: List<Int>): Unit = onOpen(row.statusId)

    override fun onReact(row: StatusRowUi, name: String, add: Boolean): Unit = onOpen(row.statusId)

    override fun onReply(row: StatusRowUi): Unit = act(row, PostAct.Reply)

    override fun onBoost(row: StatusRowUi): Unit = act(row, PostAct.Boost())

    override fun onFavourite(row: StatusRowUi): Unit = act(row, PostAct.Favourite)

    override fun onBookmark(row: StatusRowUi): Unit = act(row, PostAct.Bookmark)

    override fun onProfile(accountId: String): Unit = navigation().openAuthor(accountId)

    /** The media viewer on [statusId]'s [index]th attachment as [accountId], which holds the post. */
    public fun openMediaAs(accountId: String, statusId: String, index: Int): Unit =
        navigation().openMedia(statusId, index, accountId)

    private fun act(row: StatusRowUi, act: PostAct) {
        val url = row.url ?: return
        val account = actingAs(row) ?: return
        actAs?.act(account, url, act)
    }
}

/**
 * Every account's posts together: a post the account in use holds is acted on as on its own timeline; one
 * shown as another account ([heldBy]) is a post [Elsewhere], replied to as that account, its quotes opening
 * the post, and its pictures opening in the viewer as the account that holds it ([heldAs]: that account and
 * the post's id there), with no server asked. Boosting, favouriting and bookmarking stay with [base], whose
 * screen acts as the account the post belongs to, on its own ids there.
 */
public class MergedActions(
    private val base: StatusActions,
    private val elsewhere: Elsewhere,
    private val heldAs: (statusId: String) -> Pair<String, String>? = { null },
) : StatusActions by base {
    private fun of(id: String): StatusActions = if (isElsewhere(id)) elsewhere else base

    private fun StatusRowUi.held() = isElsewhere(statusId)

    override fun menuFor(row: StatusRowUi): Set<StatusMenuItem> = of(row.statusId).menuFor(row)

    override fun onOpen(statusId: String): Unit = of(statusId).onOpen(statusId)

    override fun onProfile(accountId: String): Unit = of(accountId).onProfile(accountId)

    override fun onMedia(row: StatusRowUi, index: Int) {
        if (!row.held()) return base.onMedia(row, index)
        heldAs(row.statusId)?.let { (account, id) -> elsewhere.openMediaAs(account, id, index) } ?: onOpen(row.statusId)
    }

    override fun onQuote(row: StatusRowUi): Unit = if (row.held()) onOpen(row.statusId) else base.onQuote(row)

    override fun onReply(row: StatusRowUi): Unit = of(row.statusId).onReply(row)

    override fun onVote(row: StatusRowUi, choices: List<Int>): Unit = of(row.statusId).onVote(row, choices)

    override fun onReact(row: StatusRowUi, name: String, add: Boolean): Unit = of(row.statusId).onReact(row, name, add)

    override fun onMenu(row: StatusRowUi, item: StatusMenuItem): Unit = of(row.statusId).onMenu(row, item)

    override fun onHistory(row: StatusRowUi): Unit = if (row.held()) onOpen(row.statusId) else base.onHistory(row)
}

private val MENU = setOf(StatusMenuItem.Share, StatusMenuItem.CopyLink, StatusMenuItem.OpenInBrowser)
