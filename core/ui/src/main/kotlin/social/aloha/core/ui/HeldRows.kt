// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource

/** Whether [id] is shown under another server's name ([heldBy]), never one of the server in use. */
public fun isElsewhere(id: String): Boolean = id.startsWith(TimelineSource.Remote.ID_PREFIX)

/** [acct], a handle on [host] or elsewhere, in full. */
private fun fullHandle(acct: String, host: String): String =
    acct.removePrefix("@").let { if ('@' in it) it else "$it@$host" }

/**
 * [this] post, as shown by an account on [host] other than the one in use: the people it mentions under that
 * server's name with their handles in full, so a tap on one opens them by handle, never by an id of [host]'s
 * taken for one on the server in use. Shown only, never stored.
 */
public fun Status.namingFrom(host: String): Status = copy(
    mentions = mentions.map {
        val full = fullHandle(it.acct, host)
        it.copy(id = TimelineSource.Remote.ID_PREFIX + full, acct = full)
    },
    reblog = reblog?.namingFrom(host),
)

/**
 * [this] row as an account other than the one in use holds it, on [host]: its ids, and its author's, under
 * that server's name, as a post read from another server is shown, and its author's handle in full. It is
 * never the reader's own here: what only its own account can do to it is done there.
 */
public fun StatusRowUi.heldBy(host: String): StatusRowUi {
    val prefix = TimelineSource.Remote.ID_PREFIX + host + ":"
    fun StatusRowUi.AuthorUi.there(): StatusRowUi.AuthorUi {
        val full = fullHandle(handle, host)
        return copy(id = TimelineSource.Remote.ID_PREFIX + full, handle = "@$full")
    }
    return copy(
        rowId = prefix + rowId,
        statusId = prefix + statusId,
        author = author.there(),
        quote = quote?.let { it.copy(statusId = prefix + it.statusId, author = it.author.there()) },
        isOwn = false,
    )
}

/** The profile of [accountId]: by its id, or, shown under another server's name, by the handle it carries. */
public fun StatusNavigation.openAuthor(accountId: String): Unit = if (isElsewhere(accountId)) {
    openProfile(null, accountId.removePrefix(TimelineSource.Remote.ID_PREFIX))
} else {
    openProfile(accountId, null)
}

/**
 * The thread of [statusId], or, shown under another server's name, the post by its [url], which the server
 * in use finds.
 */
public fun StatusNavigation.openPost(statusId: String, url: String?) {
    if (!isElsewhere(statusId)) return openThread(statusId)
    url?.let(::openWeb)
}

/** Which of the reader's accounts see a post, said for a screen reader; null where none is marked. */
@Composable
public fun seenByText(seenBy: List<SeenBy>): String? = seenBy.takeIf { it.isNotEmpty() }
    ?.let { stringResource(R.string.status_seen_by, it.joinToString(", ") { account -> account.handle }) }

/** The faces of the reader's accounts that see a post, each ringed in its own colour; said by [seenByText]. */
@Composable
public fun SeenByMark(seenBy: List<SeenBy>, size: Dp, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    StackedAvatars(
        seenBy.map { it.avatarUrl },
        size,
        modifier,
        rings = seenBy.map { account -> account.colour?.let(::Color) ?: outline },
    )
}
