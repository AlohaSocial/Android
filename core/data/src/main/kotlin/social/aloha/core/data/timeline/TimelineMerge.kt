// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

/**
 * The pure decision behind inserting a fetched page into a cached timeline, kept free of the database
 * so it can be tested exhaustively: the gap rules are where a client of this kind most often goes
 * quietly wrong.
 */
public object TimelineMerge {
    /** One row of a cached timeline, in display order; [position] strictly descends down the list. */
    public data class Slot(val statusId: String, val position: Long, val isGap: Boolean = false) {
        public companion object {
            /** A gap's id names the boundary it sits at, so closing it is idempotent. */
            public fun gap(below: String, position: Long): Slot = Slot("$GAP_PREFIX$below", position, isGap = true)
        }
    }

    /**
     * @property slots the timeline as it should be after the merge, in order.
     * @property inserted statuses this page added to the timeline.
     * @property closedGaps gap markers this page removed; [openedGaps] the ones it added.
     */
    public data class Plan(
        val slots: List<Slot>,
        val inserted: List<String> = emptyList(),
        val closedGaps: List<String> = emptyList(),
        val openedGaps: List<String> = emptyList(),
    )

    public sealed interface Direction {
        /** A refresh: the newest posts, above the newest cached row (`since_id`, or the head). */
        public data object Newer : Direction

        /** Infinite scroll: `max_id` from the oldest cached row. */
        public data object Older : Direction

        /** A cold load, or a pull that replaces everything. */
        public data object Cold : Direction

        /** Filling one gap, closed only when the page proves the two ranges now touch. */
        public data class FillingGap(val id: String) : Direction
    }

    /**
     * @param existing the cached timeline, in display order.
     * @param page what the server returned, in its order.
     * @param pageWasFull the query returned `limit` rows. Decided on what the query returned, not on what
     *   survived filtering: counting filtered rows out would make a filtered page look like the end.
     */
    public fun plan(existing: List<Slot>, page: List<String>, direction: Direction, pageWasFull: Boolean): Plan = when {
        // an empty page while filling a gap proves nothing lies between the two ranges
        page.isEmpty() && direction is Direction.FillingGap ->
            Plan(existing.filterNot { it.statusId == direction.id }, closedGaps = listOf(direction.id))

        page.isEmpty() -> Plan(existing)

        direction == Direction.Cold ->
            Plan(
                numbered(page, from = 0),
                inserted = page,
                closedGaps = existing.filter {
                    it.isGap
                }.map { it.statusId },
            )

        direction == Direction.Newer -> newer(existing, page, pageWasFull)

        direction == Direction.Older -> older(existing, page)

        else -> gapFill(existing, page, (direction as Direction.FillingGap).id, pageWasFull)
    }

    private fun newer(existing: List<Slot>, page: List<String>, pageWasFull: Boolean): Plan {
        val fresh = unknown(existing, page)
        if (fresh.isEmpty()) return Plan(existing)
        val top = existing.firstOrNull()?.position ?: 0
        val slots = numbered(fresh, from = top + fresh.size + 1).toMutableList()
        // A full page from a since_id fetch means there may be more between what arrived and what was
        // cached: a gap, rather than joining two ranges that may not touch.
        val gap = if (pageWasFull && existing.isNotEmpty()) Slot.gap(fresh.last(), slots.last().position - 1) else null
        gap?.let(slots::add)
        return Plan(slots + existing, inserted = fresh, openedGaps = listOfNotNull(gap?.statusId))
    }

    private fun older(existing: List<Slot>, page: List<String>): Plan {
        val fresh = unknown(existing, page)
        if (fresh.isEmpty()) return Plan(existing)
        val bottom = existing.lastOrNull()?.position ?: 0
        return Plan(existing + numbered(fresh, from = bottom - 1), inserted = fresh)
    }

    private fun gapFill(existing: List<Slot>, page: List<String>, gapId: String, pageWasFull: Boolean): Plan {
        val gapIndex = existing.indexOfFirst { it.statusId == gapId }
        if (gapIndex < 0) return Plan(existing)
        val above = existing.subList(0, gapIndex)
        val below = existing.subList(gapIndex + 1, existing.size)
        // what follows the first row already cached below is older than it, and not for above it
        val belowIds = below.mapTo(HashSet()) { it.statusId }
        val reaches = page.indexOfFirst { it in belowIds }
        val fresh = unknown(existing, if (reaches < 0) page else page.take(reaches))
        // A page shorter than the limit proves the ranges now touch, and so does one that reached a row
        // cached below the gap; only a full page that did neither leaves more in between, so the gap moves
        // down rather than disappearing.
        val reachedBelow = reaches >= 0
        val gapSurvives = pageWasFull && fresh.isNotEmpty() && !reachedBelow
        val upper = above.lastOrNull()?.position ?: (below.firstOrNull()?.position ?: 0) + fresh.size + 2
        val filled = fresh.mapIndexed { index, id -> Slot(id, upper - 1 - index) }
        val gap = if (gapSurvives) Slot.gap(fresh.last(), upper - 1 - fresh.size) else null
        val middle = filled + listOfNotNull(gap)
        return Plan(
            above + middle + pushedBelow(below, middle.lastOrNull()?.position ?: upper),
            inserted = fresh,
            closedGaps = listOf(gapId),
            openedGaps = listOfNotNull(gap?.statusId),
        )
    }

    /**
     * The rows under a filled gap, moved down only where they would collide with what was filled in above
     * them. A page can hold more rows than the numbers left between the two ranges; spacing them out
     * evenly, as the Apple app does, then puts two rows at one position.
     */
    private fun pushedBelow(below: List<Slot>, lowestAbove: Long): List<Slot> {
        var ceiling = lowestAbove
        return below.map { slot ->
            slot.copy(position = minOf(slot.position, ceiling - 1)).also { ceiling = it.position }
        }
    }

    private fun unknown(existing: List<Slot>, page: List<String>): List<String> {
        val known = existing.mapTo(HashSet()) { it.statusId }
        return page.filter { known.add(it) }
    }

    private fun numbered(ids: List<String>, from: Long): List<Slot> = ids.mapIndexed { offset, id ->
        Slot(
            id,
            from - offset,
        )
    }

    /** Whether the timeline is ordered and free of duplicates, which the merge tests hold every plan to. */
    public fun isWellFormed(slots: List<Slot>): Boolean {
        val seen = HashSet<String>()
        return slots.zipWithNext().all { (upper, lower) -> lower.position < upper.position } &&
            slots.all { seen.add(it.statusId) }
    }

    public const val GAP_PREFIX: String = "gap:"
}
