// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import social.aloha.core.model.Status
import social.aloha.core.testing.StatusSamples

class ThreadShapeTest {
    private fun reply(id: String, to: String?): Status = StatusSamples.post().copy(id = id, inReplyToId = to)

    @Test
    fun `ancestors come first, then the focused post, then replies depth first in the order sent`() {
        val lines = ThreadShape.of(
            "f",
            ancestors = listOf(reply("a1", null), reply("a2", "a1")),
            descendants = listOf(reply("r1", "f"), reply("r1a", "r1"), reply("r2", "f"), reply("r1b", "r1")),
        )
        assertEquals(
            listOf(
                ThreadLine.Ancestor("a1"),
                ThreadLine.Ancestor("a2"),
                ThreadLine.Focused("f"),
                ThreadLine.Reply("r1", 1),
                ThreadLine.Reply("r1a", 2),
                ThreadLine.Reply("r1b", 2),
                ThreadLine.Reply("r2", 1),
            ),
            lines,
        )
    }

    @Test
    fun `replies stop indenting at five levels, and what lies deeper collapses with its count`() {
        val chain = (1..8).map { reply("d$it", if (it == 1) "f" else "d${it - 1}") } + reply("side", "d6")
        val lines = ThreadShape.of("f", emptyList(), chain)
        assertEquals(
            listOf(
                ThreadLine.Focused("f"),
                ThreadLine.Reply("d1", 1),
                ThreadLine.Reply("d2", 2),
                ThreadLine.Reply("d3", 3),
                ThreadLine.Reply("d4", 4),
                ThreadLine.Reply("d5", 5),
                ThreadLine.More("d5", count = 4, depth = 5),
            ),
            lines,
        )
    }

    @Test
    fun `a reply whose parent was not sent still shows, among the direct replies`() {
        val lines = ThreadShape.of("f", emptyList(), listOf(reply("r1", "f"), reply("orphan", "missing")))
        assertEquals(listOf(ThreadLine.Focused("f"), ThreadLine.Reply("r1", 1), ThreadLine.Reply("orphan", 1)), lines)
    }
}
