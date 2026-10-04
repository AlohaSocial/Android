// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadShapeWithoutTest {
    @Test
    fun `a held reply leaves with everything under it, and its siblings stay`() {
        val lines = listOf(
            ThreadLine.Focused("f"),
            ThreadLine.Reply("r1", 1),
            ThreadLine.Reply("r1a", 2),
            ThreadLine.More("r1a", count = 2, depth = 2),
            ThreadLine.Reply("r2", 1),
        )
        assertEquals(listOf("f", "r2"), ThreadShape.without(lines, setOf("r1")).map { it.key })
        assertEquals(lines, ThreadShape.without(lines, emptySet()))
    }
}
