// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import social.aloha.core.model.TimelineKey
import social.aloha.core.network.endpoints.PageAnchor

/** What a refresh asks the server for, and how the answer merges. */
public data class RefreshPlan(val anchor: PageAnchor, val direction: TimelineMerge.Direction) {
    public companion object {
        /**
         * A timeline that has never fetched asks for the head, even when the cache handed it rows to draw:
         * "I have rows" is not "I have fetched". Deciding from the rows alone left a stale cache on screen
         * when the server had nothing newer than it. After a fetch it asks for the newest posts above the
         * top row; a fetched but empty timeline goes cold rather than anchoring to nothing.
         *
         * Asking for the head is not replacing the cache: while there are rows, the head merges as newer
         * than them, so a page that overlaps joins them, a full one opens a gap above them, and the rows
         * and the reading position on them survive a launch. Only an empty timeline merges cold.
         *
         * Newest means `since_id`, not `min_id`: `min_id` answers with the posts immediately above the
         * anchor, so after a busy hour a refresh showed the oldest of the new posts and a full page left
         * its hole above the page, where the merge puts no gap. With `since_id` the page is the head, and
         * a full one leaves the hole between it and the cache, which is where the gap goes.
         *
         * A [keptOrder] timeline's ids are not where its rows sit, so it always takes its head afresh.
         */
        public fun of(hasFetchedBefore: Boolean, newestRowId: String?, keptOrder: Boolean = false): RefreshPlan = when {
            newestRowId == null || keptOrder -> RefreshPlan(PageAnchor.Cold, TimelineMerge.Direction.Cold)
            hasFetchedBefore -> RefreshPlan(PageAnchor.ImmediatelyAfter(newestRowId), TimelineMerge.Direction.Newer)
            else -> RefreshPlan(PageAnchor.Cold, TimelineMerge.Direction.Newer)
        }
    }
}

/** How much of each kind the disposable cache keeps, and how much one sweep may delete. */
public object CachePolicy {
    public const val HOME_TIMELINE_ROWS: Int = 500
    public const val OTHER_TIMELINE_ROWS: Int = 200
    public const val ORPHAN_STATUS_DAYS: Long = 7
    public const val MAXIMUM_DELETIONS_PER_SWEEP: Int = 2_000

    public fun rowsFor(timelineKey: String): Int = if (timelineKey == HOME_KEY) {
        HOME_TIMELINE_ROWS
    } else {
        OTHER_TIMELINE_ROWS
    }

    private val HOME_KEY = TimelineKey.home().storageKey
}
