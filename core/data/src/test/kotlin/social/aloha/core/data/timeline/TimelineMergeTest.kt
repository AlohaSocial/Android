// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.data.timeline.TimelineMerge.Direction
import social.aloha.core.data.timeline.TimelineMerge.Slot
import social.aloha.core.network.endpoints.PageAnchor

private fun slots(vararg ids: String) = ids.mapIndexed { index, id -> Slot(id, 1000L - index * 10) }

private fun List<Slot>.ids() = map { it.statusId }

class TimelineMergeTest {
    @Test
    fun `a cold load takes the page as the whole timeline`() {
        val plan = TimelineMerge.plan(emptyList(), listOf("5", "4", "3"), Direction.Cold, pageWasFull = false)
        assertEquals(listOf("5", "4", "3"), plan.slots.ids())
        assertEquals(listOf("5", "4", "3"), plan.inserted)
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a short refresh joins directly, with no gap`() {
        val plan = TimelineMerge.plan(slots("3", "2", "1"), listOf("5", "4"), Direction.Newer, pageWasFull = false)
        assertEquals(listOf("5", "4", "3", "2", "1"), plan.slots.ids())
        assertTrue(plan.openedGaps.isEmpty())
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    /** Joining two ranges that may not touch leaves a timeline with invisible holes. */
    @Test
    fun `a full refresh inserts a gap rather than joining two ranges`() {
        val plan = TimelineMerge.plan(slots("3", "2", "1"), listOf("9", "8", "7"), Direction.Newer, pageWasFull = true)
        assertEquals(listOf("9", "8", "7"), plan.slots.ids().take(3))
        assertTrue(plan.slots[3].isGap)
        assertEquals(listOf("3", "2", "1"), plan.slots.ids().takeLast(3))
        assertEquals(1, plan.openedGaps.size)
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a refresh into an empty timeline never opens a gap`() {
        val plan = TimelineMerge.plan(emptyList(), listOf("3", "2", "1"), Direction.Newer, pageWasFull = true)
        assertTrue(plan.openedGaps.isEmpty())
        assertTrue(plan.slots.none { it.isGap })
    }

    @Test
    fun `paging older appends below and never duplicates`() {
        val plan = TimelineMerge.plan(slots("5", "4"), listOf("4", "3", "2"), Direction.Older, pageWasFull = false)
        assertEquals(listOf("5", "4", "3", "2"), plan.slots.ids())
        assertEquals(listOf("3", "2"), plan.inserted)
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a short page closes the gap it was fetched for`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + listOf(Slot("3", 960), Slot("2", 950))
        val plan = TimelineMerge.plan(
            existing,
            listOf("7", "6", "5", "4"),
            Direction.FillingGap("gap:8"),
            pageWasFull = false,
        )
        assertEquals(listOf("9", "8", "7", "6", "5", "4", "3", "2"), plan.slots.ids())
        assertEquals(listOf("gap:8"), plan.closedGaps)
        assertTrue(plan.openedGaps.isEmpty())
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a full page moves the gap down instead of closing it`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + Slot("2", 960)
        val plan = TimelineMerge.plan(
            existing,
            listOf("7", "6", "5"),
            Direction.FillingGap("gap:8"),
            pageWasFull = true,
        )
        assertEquals("9", plan.slots.ids().first())
        assertEquals("2", plan.slots.ids().last())
        assertEquals(listOf("gap:8"), plan.closedGaps)
        assertEquals(1, plan.openedGaps.size)
        assertEquals(1, plan.slots.count { it.isGap })
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a gap filled from below takes the posts just above the row under it, and a short page closes it`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + listOf(Slot("3", 960), Slot("2", 950))
        val plan = TimelineMerge.plan(
            existing,
            listOf("6", "5", "4"),
            Direction.FillingGap("gap:8", fromBelow = true),
            pageWasFull = false,
        )
        assertEquals(listOf("9", "8", "6", "5", "4", "3", "2"), plan.slots.ids())
        assertEquals(listOf("gap:8"), plan.closedGaps)
        assertTrue(plan.openedGaps.isEmpty())
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a full page from below leaves the gap above what it filled, where the reader has not read yet`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + listOf(Slot("3", 960), Slot("2", 950))
        val plan = TimelineMerge.plan(
            existing,
            listOf("6", "5", "4"),
            Direction.FillingGap("gap:8", fromBelow = true),
            pageWasFull = true,
        )
        assertEquals(listOf("9", "8", "gap:6", "6", "5", "4", "3", "2"), plan.slots.ids())
        assertEquals(listOf("gap:6"), plan.openedGaps)
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a page from below that reaches the rows above closes the gap and adds only what lies under them`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + listOf(Slot("3", 960))
        val plan = TimelineMerge.plan(
            existing,
            listOf("9", "8", "5", "4"),
            Direction.FillingGap("gap:8", fromBelow = true),
            pageWasFull = true,
        )
        assertEquals(listOf("9", "8", "5", "4", "3"), plan.slots.ids())
        assertTrue(plan.openedGaps.isEmpty())
        assertEquals(listOf("5", "4"), plan.inserted)
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `an empty gap-fill page proves the ranges touch and closes the gap`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + Slot("7", 960)
        val plan = TimelineMerge.plan(existing, emptyList(), Direction.FillingGap("gap:8"), pageWasFull = false)
        assertEquals(listOf("9", "8", "7"), plan.slots.ids())
        assertEquals(listOf("gap:8"), plan.closedGaps)
    }

    @Test
    fun `a timeline in kept order always takes its head afresh`() {
        assertEquals(
            RefreshPlan(PageAnchor.Cold, Direction.Cold),
            RefreshPlan.of(hasFetchedBefore = true, newestRowId = "42", keptOrder = true),
        )
    }

    @Test
    fun `a full page the device filtered down to nothing leaves the gap open`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + Slot("7", 960)
        val plan = TimelineMerge.plan(existing, emptyList(), Direction.FillingGap("gap:8"), pageWasFull = true)
        assertEquals(existing, plan.slots)
        assertTrue(plan.closedGaps.isEmpty())
    }

    @Test
    fun `re-fetching the same page changes nothing`() {
        val plan = TimelineMerge.plan(slots("3", "2", "1"), listOf("3", "2", "1"), Direction.Newer, pageWasFull = false)
        assertTrue(plan.inserted.isEmpty())
        assertEquals(listOf("3", "2", "1"), plan.slots.ids())
    }

    @Test
    fun `positions stay strictly descending through every operation`() {
        var current = slots("5", "4", "3")
        current = TimelineMerge.plan(current, listOf("9", "8", "7"), Direction.Newer, pageWasFull = true).slots
        assertTrue(TimelineMerge.isWellFormed(current))
        current = TimelineMerge.plan(current, listOf("2", "1"), Direction.Older, pageWasFull = false).slots
        assertTrue(TimelineMerge.isWellFormed(current))
        val gap = current.first { it.isGap }.statusId
        current = TimelineMerge.plan(current, listOf("6"), Direction.FillingGap(gap), pageWasFull = false).slots
        assertTrue(TimelineMerge.isWellFormed(current))
        assertEquals(listOf("9", "8", "7", "6", "5", "4", "3", "2", "1"), current.ids())
    }

    @Test
    fun `a gap fill that reaches the rows below keeps what lies past them out from above them`() {
        val existing = slots("9", "8") + Slot.gap("8", 970) + listOf(Slot("5", 960), Slot("4", 950))
        // 3 comes after 5 on the page: it is older than 5 and must not be filled in above it
        val plan = TimelineMerge.plan(
            existing,
            listOf("7", "6", "5", "3"),
            Direction.FillingGap("gap:8"),
            pageWasFull = true,
        )
        assertEquals(listOf("9", "8", "7", "6", "5", "4"), plan.slots.ids())
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }

    @Test
    fun `a duplicated or misordered timeline is detected`() {
        assertFalse(TimelineMerge.isWellFormed(listOf(Slot("a", 10), Slot("a", 9))))
        assertFalse(TimelineMerge.isWellFormed(listOf(Slot("a", 9), Slot("b", 10))))
    }

    /** The Apple app spaced the filled rows evenly and put two rows at one position once a page outgrew the room. */
    @Test
    fun `a gap narrower than the page still fills in order`() {
        val existing = listOf(Slot("top", 100), Slot.gap("top", 99), Slot("below", 98), Slot("bottom", 97))
        val page = (1..20).map { "p$it" }
        val plan = TimelineMerge.plan(existing, page, Direction.FillingGap("gap:top"), pageWasFull = true)
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
        assertEquals(listOf("top") + page + listOf("gap:p20", "below", "bottom"), plan.slots.ids())
    }

    /** The Apple app kept the gap whenever the page was full, even when the page ran into the rows below it. */
    @Test
    fun `a full page that reaches the rows below the gap closes it`() {
        val existing = slots("20", "19") + Slot.gap("19", 970) + listOf(Slot("10", 960), Slot("9", 950))
        val plan = TimelineMerge.plan(
            existing,
            listOf("18", "17", "10", "9"),
            Direction.FillingGap("gap:19"),
            pageWasFull = true,
        )
        assertEquals(listOf("20", "19", "18", "17", "10", "9"), plan.slots.ids())
        assertEquals(listOf("gap:19"), plan.closedGaps)
        assertTrue(plan.openedGaps.isEmpty())
    }

    @Test
    fun `a page that repeats an id inserts it once`() {
        val plan = TimelineMerge.plan(slots("1"), listOf("3", "3", "2"), Direction.Newer, pageWasFull = false)
        assertEquals(listOf("3", "2", "1"), plan.slots.ids())
        assertTrue(TimelineMerge.isWellFormed(plan.slots))
    }
}

/** The four lines that decide what a refresh asks for; they cost a visible bug once and nothing failed. */
class RefreshPlanTest {
    @Test
    fun `a timeline that has never fetched asks for the head, and merges it with the rows it has`() {
        assertEquals(
            RefreshPlan(PageAnchor.Cold, Direction.Newer),
            RefreshPlan.of(hasFetchedBefore = false, newestRowId = "42"),
        )
        assertEquals(
            RefreshPlan(PageAnchor.Cold, Direction.Cold),
            RefreshPlan.of(hasFetchedBefore = false, newestRowId = null),
        )
    }

    @Test
    fun `once it has fetched, it asks for the newest posts above the top row`() {
        assertEquals(
            RefreshPlan(PageAnchor.ImmediatelyAfter("42"), Direction.Newer),
            RefreshPlan.of(hasFetchedBefore = true, newestRowId = "42"),
        )
    }

    @Test
    fun `a fetched but empty timeline goes cold rather than anchoring to nothing`() {
        assertEquals(
            RefreshPlan(PageAnchor.Cold, Direction.Cold),
            RefreshPlan.of(hasFetchedBefore = true, newestRowId = null),
        )
    }

    @Test
    fun `rows on screen are never merged away, and a refresh never pages older`() {
        val cases = listOf(true, false).flatMap { fetched -> listOf("42", null).map { fetched to it } }
        for ((fetched, newest) in cases) {
            val plan = RefreshPlan.of(fetched, newest)
            assertEquals(if (newest == null) Direction.Cold else Direction.Newer, plan.direction)
            assertTrue(plan.anchor is PageAnchor.Cold || plan.anchor is PageAnchor.ImmediatelyAfter, "${plan.anchor}")
        }
    }
}
