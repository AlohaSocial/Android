// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleSlotsTest {
    @Test
    fun `while who they are loads, a button keeps one circle per person the count names, up to four`() {
        assertEquals(listOf(null), peopleSlots(null, 1))
        assertEquals(listOf(null, null), peopleSlots(null, 2))
        assertEquals(List(4) { null }, peopleSlots(null, 12))
    }

    @Test
    fun `once known, the people the server named take the room, however many the count says`() {
        assertEquals(listOf("a", "b"), peopleSlots(listOf("a", "b"), 2))
        assertEquals(listOf("a"), peopleSlots(listOf("a"), 3))
        assertEquals(listOf("a", "b", "c", "d"), peopleSlots(listOf("a", "b", "c", "d", "e"), 5))
    }

    @Test
    fun `a count or a list of none draws none`() {
        assertNull(peopleSlots(null, 0))
        assertNull(peopleSlots(null, null))
        assertNull(peopleSlots(emptyList(), 3))
    }
}
