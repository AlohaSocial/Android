// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Instant
import social.aloha.core.model.Status

/**
 * One account's part of the merged timeline: the posts it shows, already filtered by its own settings, and
 * how far down its stored rows reach without a hole, which it measures before any filtering.
 *
 * @property reach the time of the oldest post above the account's first gap, or null with none stored.
 * @property bounds whether the account can bring more from below [reach]: it has rows, and its server or a
 *   gap has more under them. One that has nothing yet, or has reached its end, bounds nothing.
 */
public data class AccountRows(
    val accountId: String,
    val posts: List<Status>,
    val reach: Instant?,
    val bounds: Boolean,
) {
    public companion object {
        /**
         * [stored], the account's timeline as stored, newest first, with what [shows] keeps of it; [reachedEnd]
         * when its server has nothing further down. Rows below the first gap wait until it is filled: their
         * place among the other accounts' is not known before.
         */
        public fun of(
            accountId: String,
            stored: List<TimelineRow>,
            reachedEnd: Boolean,
            shows: (Status) -> Boolean = { true },
        ): AccountRows {
            val gap = stored.indexOfFirst { it is TimelineRow.Gap }
            val run = (if (gap >= 0) stored.subList(0, gap) else stored).filterIsInstance<TimelineRow.Post>()
            val reach = run.lastOrNull()?.status?.createdAt
            return AccountRows(
                accountId,
                posts = run.map(TimelineRow.Post::status).filter(shows),
                reach = reach,
                bounds = reach != null && (gap >= 0 || !reachedEnd),
            )
        }
    }
}

/**
 * A post of the merged timeline: [status] as its [owner] holds it, with that account's ids, so whatever is
 * done to it goes out as the owner; [seenBy] every account that has it, the owner first.
 */
public data class MergedPost(val owner: String, val status: Status, val seenBy: List<String>) {
    /** Stable across refreshes and accounts: what the post is wherever it was seen. */
    val key: String get() = MergedTimeline.identity(owner, status)
}

/**
 * The merged timeline as far as every account has loaded it: [posts], newest first, and [behind], the
 * accounts whose rows end where it ends, which bring the next page; empty once none has more.
 */
public data class MergedPage(val posts: List<MergedPost>, val behind: List<String>)

/**
 * Every account's timeline as one, the way a mail client shows all inboxes: each post once however many
 * accounts see it, newest first.
 *
 * - **Once:** a post is known by its `uri`, the address it has wherever it is seen, a boost by the post it
 *   boosts. It acts as the first account, in the order given, that has it, and shows as that account
 *   holds it; it stands as high as the newest of its sightings, a boost by when it was boosted.
 * - **As far as every account reached:** accounts load to different depths. Below the newest of the
 *   bounding accounts' reaches, one of them may still have posts it has not loaded, which would belong in
 *   between; the merged list stops there, and that account loads next.
 */
public object MergedTimeline {
    /** [parts] in account order, the first being the one a post shared by several acts as. */
    public fun merge(parts: List<AccountRows>): MergedPage {
        val frontier = parts.filter { it.bounds }.mapNotNull { it.reach }.maxOrNull()
        val sightings = LinkedHashMap<String, MutableList<Pair<String, Status>>>()
        parts.forEach { part ->
            part.posts.filter { frontier == null || !it.createdAt.isBefore(frontier) }.forEach { status ->
                sightings.getOrPut(identity(part.accountId, status)) { mutableListOf() } += part.accountId to status
            }
        }
        val order = parts.map(AccountRows::accountId)
        val posts = sightings.values.map { seen ->
            val (owner, status) = seen.minBy { (account, _) -> order.indexOf(account) }
            MergedPost(owner, status, seenBy = seen.map { it.first }.distinct().sortedBy(order::indexOf)) to
                seen.maxOf { it.second.createdAt }
        }
        val newestFirst = compareByDescending<Pair<MergedPost, Instant>> { it.second }
            .thenBy { order.indexOf(it.first.owner) }
            .thenByDescending { it.first.status.id }
        return MergedPage(
            posts = posts.sortedWith(newestFirst).map { it.first },
            behind = parts.filter { it.bounds && it.reach == frontier }.map(AccountRows::accountId),
        )
    }

    /**
     * Read down to [at], said per account: the newest of its posts in [stored], by account id, that is no
     * newer, which its home read marker moves up to. An account with nothing that old is left out.
     */
    public fun readUpTo(stored: Map<String, List<TimelineRow>>, at: Instant): Map<String, String> =
        stored.mapNotNull { (account, rows) ->
            rows.firstOrNull { it is TimelineRow.Post && !it.status.createdAt.isAfter(at) }?.let { account to it.id }
        }.toMap()

    /** What a post is wherever it is seen: its `uri`, or, where a server sent none, the one account's own id. */
    internal fun identity(accountId: String, status: Status): String =
        status.displayed.uri.ifBlank { "$accountId/${status.displayed.id}" }
}
