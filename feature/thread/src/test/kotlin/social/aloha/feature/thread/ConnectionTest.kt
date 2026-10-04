// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.html.RichTextCache
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

class ConnectionTest {
    private val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))

    private fun post(id: String, depth: Int, focused: Boolean = false) =
        ThreadItem.Post(mapper.map(StatusSamples.post().copy(id = id), "1", showContext = false), focused, depth)

    @Test
    fun `ancestors join the focused post, which sends no line down, and a reply joins the one it answers`() {
        val items = listOf(
            post("a1", 0),
            post("a2", 0),
            post("f", 0, focused = true),
            post("r1", 1),
            post("r1a", 2),
            post("r2", 1),
            ThreadItem.More("r2", count = 3, depth = 1),
        )
        assertEquals(
            listOf(
                Connection(up = false, down = true),
                Connection(up = true, down = true),
                Connection(up = true, down = false),
                Connection(up = false, down = true),
                Connection(up = true, down = false),
                Connection(up = false, down = false),
                Connection(up = false, down = false),
            ),
            connections(items),
        )
    }
}
