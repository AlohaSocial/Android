// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Card
import social.aloha.core.model.Reaction
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.Status
import social.aloha.core.model.StatusEdit
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowCache
import social.aloha.core.ui.toAnnotatedString

/** How a laid-out thread becomes what the screen draws. */
internal object ThreadPresentation {
    /** The rows of [lines], [shown] by id (the focused post with its extras); a post not stored is left out. */
    fun items(
        lines: List<ThreadLine>,
        shown: Map<String, Status>,
        rows: StatusRowCache,
        viewer: String,
    ): List<ThreadItem> = lines.mapNotNull { line ->
        if (line is ThreadLine.More) return@mapNotNull ThreadItem.More(line.parentId, line.count, line.depth)
        shown[line.statusId]?.let {
            val depth = (line as? ThreadLine.Reply)?.depth ?: 0
            ThreadItem.Post(rows.rowFor(it, viewer, null), line is ThreadLine.Focused, depth)
        }
    }

    /** The focused post with what only the thread fetches: its card, when it carries none, and its reactions. */
    fun withExtras(status: Status, card: Card?, reactions: List<Reaction>?): Status {
        val shown = status.displayed
        val extended = shown.copy(card = shown.card ?: card, reactions = reactions ?: shown.reactions)
        return if (status.reblog != null) status.copy(reblog = extended) else extended
    }

    /** The lists worth a button: who favourited or boosted, when anyone did; quotes and reactions where served. */
    fun lists(shown: Status, capabilities: ServerCapabilities): Map<StatusListKind, Int?> = buildMap {
        if (shown.favouritesCount > 0) put(StatusListKind.FavouritedBy, shown.favouritesCount)
        if (shown.reblogsCount > 0) put(StatusListKind.BoostedBy, shown.reblogsCount)
        if (capabilities.quotePosts) put(StatusListKind.Quotes, null)
        shown.reactions?.takeIf { it.isNotEmpty() }?.let { put(StatusListKind.Reactions, it.sumOf(Reaction::count)) }
    }

    fun versions(edits: List<StatusEdit>, colors: RichTextColors): List<EditVersion> = edits.map { edit ->
        EditVersion(
            createdAt = edit.createdAt,
            spoiler = edit.spoilerText.takeIf { it.isNotBlank() },
            body = StatusHtmlParser.parse(edit.content).toAnnotatedString(colors),
        )
    }
}
