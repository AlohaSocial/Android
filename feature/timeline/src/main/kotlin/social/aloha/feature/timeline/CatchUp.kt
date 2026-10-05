// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.runtime.Immutable
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.model.ServerIds
import social.aloha.core.model.Status

/** The order of the posts caught up on: newest first, or those most talked about or most passed on. */
internal enum class CatchUpSort { Time, Replies, Boosts }

/** Which posts are caught up on: all, posts of their own, boosts, replies, or those with pictures or video. */
internal enum class CatchUpKind {
    All,
    Posts,
    Boosts,
    Replies,
    Media,
    ;

    fun admits(status: Status): Boolean = when (this) {
        All -> true
        Posts -> status.reblog == null && status.inReplyToId == null
        Boosts -> status.reblog != null
        Replies -> status.displayed.inReplyToId != null
        Media -> status.displayed.mediaAttachments.isNotEmpty()
    }
}

/** What the reader chose to see of what arrived; [person] is the id of whoever put a post on Home. */
@Immutable
internal data class CatchUpChoice(
    val sort: CatchUpSort = CatchUpSort.Time,
    val kind: CatchUpKind = CatchUpKind.All,
    val person: String? = null,
)

/** Someone who put posts on Home since, and how many. */
@Immutable
internal data class CatchUpPerson(val id: String, val name: String, val posts: Int)

/**
 * What arrived on Home after [since], newest first, from the stored [rows]; the gaps in them skipped. With
 * nothing read yet, the newest [MOST] of them.
 */
internal fun newsOf(rows: List<TimelineRow>, since: String?): List<Status> = rows.asSequence()
    .filterIsInstance<TimelineRow.Post>().map { it.status }
    .takeWhile { since == null || ServerIds.isNewer(it.id, since) }
    .take(MOST).toList()

/** Of [news], what [choice] keeps, in its order. */
internal fun chosen(news: List<Status>, choice: CatchUpChoice): List<Status> {
    val kept = news.filter { choice.kind.admits(it) && (choice.person == null || it.account.id == choice.person) }
    return when (choice.sort) {
        CatchUpSort.Time -> kept
        CatchUpSort.Replies -> kept.sortedByDescending { it.displayed.repliesCount }
        CatchUpSort.Boosts -> kept.sortedByDescending { it.displayed.reblogsCount }
    }
}

/** Whoever put [news] on Home, those with most first. */
internal fun peopleOf(news: List<Status>): List<CatchUpPerson> = news.groupBy { it.account.id }
    .map { (id, posts) -> CatchUpPerson(id, posts.first().account.bestDisplayName, posts.size) }
    .sortedByDescending { it.posts }

private const val MOST = 200
