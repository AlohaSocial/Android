// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderingTest {
    private var told: List<String>? = null
    private val reordering = Reordering<String>(key = { it }, gap = 10f).apply {
        latest = listOf("a", "b", "c")
        onOrder = { told = it }
        listOf("a", "b", "c").forEach { heights[it] = 100 }
    }

    @Test
    fun `an item dragged past half its neighbour changes places, and the order is told when let go`() {
        reordering.start("a")
        reordering.drag(40f)
        assertEquals(listOf("a", "b", "c"), reordering.order)
        reordering.drag(20f)
        assertEquals(listOf("b", "a", "c"), reordering.order)
        assertEquals(-50f, reordering.offset)
        assertNull(told)
        reordering.stop()
        assertEquals(listOf("b", "a", "c"), told)
        assertNull(reordering.dragged)
        // shown as moved until the list given changes
        assertEquals(listOf("b", "a", "c"), reordering.order)
        reordering.latest = listOf("c", "b", "a")
        assertEquals(listOf("c", "b", "a"), reordering.order)
    }

    @Test
    fun `an item that changes while it is dragged keeps its place`() {
        val keyed = Reordering<Pair<String, Int>>(key = { it.first }, gap = 0f).apply {
            latest = listOf("a" to 0, "b" to 0)
            listOf("a", "b").forEach { heights[it] = 100 }
        }
        keyed.start("a" to 0)
        keyed.drag(60f)
        keyed.latest = listOf("a" to 40, "b" to 0)
        assertEquals(listOf("b" to 0, "a" to 40), keyed.order)
    }

    @Test
    fun `a screen reader's move is told at once`() {
        reordering.move(2, 1)
        assertEquals(listOf("a", "c", "b"), told)
    }
}
