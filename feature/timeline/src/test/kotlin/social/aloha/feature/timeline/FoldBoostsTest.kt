// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.html.RichTextCache
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

@RunWith(RobolectricTestRunner::class)
class FoldBoostsTest {
    private val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))

    private fun boost(id: String) = TimelineItem.Post(mapper.map(StatusSamples.boost.copy(id = id), "1", null))

    private fun post(id: String) = TimelineItem.Post(mapper.map(StatusSamples.post().copy(id = id), "1", null))

    @Test
    fun `three boosts in a row fold into one item and two stay as posts`() {
        val items = listOf(boost("a"), boost("b"), boost("c"), post("p"), boost("d"), boost("e"))
        val folded = foldBoosts(items, emptySet())
        assertEquals(listOf("a", "p", "d", "e"), folded.map { it.key })
        assertEquals(listOf("a", "b", "c"), (folded.first() as TimelineItem.Boosts).rows.map { it.rowId })
    }

    @Test
    fun `a gap or the caught-up line between boosts ends the run`() {
        val items = listOf(boost("a"), boost("b"), TimelineItem.CaughtUp, boost("c"), boost("d"))
        assertEquals(items, foldBoosts(items, emptySet()))
    }

    @Test
    fun `an unfolded run stays as posts, even once a newer boost joins it`() {
        val items = listOf(boost("new"), boost("a"), boost("b"), boost("c"))
        assertEquals(items, foldBoosts(items, setOf("a")))
    }

    @Test
    fun `a saved position at a folded post finds the folded item`() {
        val folded = foldBoosts(listOf(boost("a"), boost("b"), boost("c")), emptySet())
        assertEquals(0, folded.indexOfFirst { it.shows("b") })
    }
}
