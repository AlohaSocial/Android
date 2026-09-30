// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.model.Reaction

class ReactionsTest {
    private val heart = "❤️"
    private val party = "🎉"

    @Test
    fun `a reaction of one's own counts once, and taking it back leaves what others gave`() {
        val others = listOf(Reaction(heart, count = 2))
        val mine = ThreadPresentation.reacted(others, heart, add = true)
        assertEquals(listOf(Reaction(heart, count = 3, me = true)), mine)
        assertEquals(mine, ThreadPresentation.reacted(mine, heart, add = true))
        assertEquals(others, ThreadPresentation.reacted(mine, heart, add = false))
    }

    @Test
    fun `a new reaction is added, and one nobody is left giving goes`() {
        val added = ThreadPresentation.reacted(listOf(Reaction(heart, count = 1)), party, add = true)
        assertEquals(listOf(Reaction(heart, count = 1), Reaction(party, count = 1, me = true)), added)
        assertEquals(listOf(Reaction(heart, count = 1)), ThreadPresentation.reacted(added, party, add = false))
    }
}
