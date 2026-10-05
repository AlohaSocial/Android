// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.testing.StatusSamples

class CatchUpTest {
    private val own = StatusSamples.post().copy(id = "50", repliesCount = 1, reblogsCount = 9)
    private val reply = StatusSamples.reply.copy(id = "40", repliesCount = 7, reblogsCount = 0)
    private val boost = StatusSamples.boost.copy(id = "30")
    private val read = StatusSamples.post().copy(id = "20")
    private val rows = listOf(own, TimelineRow.Gap("45"), reply, boost, read).map {
        if (it is TimelineRow) it else TimelineRow.Post(it as social.aloha.core.model.Status)
    }

    @Test
    fun `what arrived is what is newer than the last read, the gaps skipped`() {
        assertEquals(listOf("50", "40", "30"), newsOf(rows, since = "20").map { it.id })
        assertEquals(4, newsOf(rows, since = null).size)
    }

    @Test
    fun `a kind, a person and an order pick from it`() {
        val news = newsOf(rows, since = "20")
        assertEquals(listOf("30"), chosen(news, CatchUpChoice(kind = CatchUpKind.Boosts)).map { it.id })
        assertEquals(listOf("40"), chosen(news, CatchUpChoice(kind = CatchUpKind.Replies)).map { it.id })
        assertEquals(listOf("50"), chosen(news, CatchUpChoice(kind = CatchUpKind.Posts)).map { it.id })
        assertEquals("40", chosen(news, CatchUpChoice(sort = CatchUpSort.Replies)).first().id)
        val booster = boost.account.id
        assertEquals(listOf("30"), chosen(news, CatchUpChoice(person = booster)).map { it.id })
    }
}
