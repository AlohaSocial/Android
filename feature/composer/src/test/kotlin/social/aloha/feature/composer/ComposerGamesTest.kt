// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerGamesTest {
    private val words = ComposerGames.Words("heads", "tails") { choice, options -> "$choice (out of $options)" }

    // a source that always answers the same, so each game lands where the test says
    private fun play(text: String, value: Double = 0.0) = ComposerGames.play(text, words) { value }

    @Test
    fun `a die has six sides unless told otherwise, and names them when not six`() {
        assertEquals("🎲 1", play("/dice"))
        assertEquals("🎲 6", play("/roll", value = 0.99))
        assertEquals("🎲 20 (d20)", play("/dice 20", value = 0.99))
        assertEquals("🎲 1 (d20)", play("/dice d20"))
    }

    @Test
    fun `a die's sides are held between two and a thousand`() {
        assertEquals("🎲 1 (d2)", play("/dice 1"))
        assertEquals("🎲 1000 (d1000)", play("/dice 9999", value = 0.99999))
    }

    @Test
    fun `a coin lands on either side`() {
        assertEquals("🪙 heads", play("/flip", value = 0.2))
        assertEquals("🪙 tails", play("/FLIP", value = 0.7))
    }

    @Test
    fun `pick takes the rest of its line, split on commas or on or`() {
        assertEquals("🎯 pizza (out of pizza, pasta)\nlater", play("/pick pizza, pasta\nlater"))
        assertEquals("🎯 tea (out of tea, coffee, water)", play("/pick tea or coffee or water"))
        assertEquals("🎯 b (out of a, b)", play("/pick a, , b,", value = 0.99))
    }

    @Test
    fun `fewer than two options is not a game and stays as typed`() {
        assertEquals("/pick pizza", play("/pick pizza"))
        assertEquals("/pick", play("/pick"))
    }

    @Test
    fun `a command counts only at the start of a line or after whitespace`() {
        assertEquals("see https://x.test/dice and a/flip", play("see https://x.test/dice and a/flip"))
        assertEquals("roll: 🎲 1", play("roll: /dice"))
        assertEquals("one\n🎲 1", play("one\n/dice"))
    }

    @Test
    fun `a command's name must end where a word would`() {
        assertEquals("/dice20 /flipped /dice/x", play("/dice20 /flipped /dice/x"))
        assertEquals("🎲 1, 🪙 heads!", play("/dice, /flip!"))
    }

    @Test
    fun `several games in one post each play, and a pick does not swallow the next line`() {
        assertEquals("🪙 heads then 🎲 1", play("/flip then /dice"))
        assertEquals(
            listOf(ComposerGames.Kind.Flip, ComposerGames.Kind.Dice, ComposerGames.Kind.Pick),
            ComposerGames.kinds("/flip /dice /flip\n/pick a, b"),
        )
    }

    @Test
    fun `pick keeps at most twenty options`() {
        val many = (1..25).joinToString(", ") { "o$it" }
        assertEquals(20, ComposerGames.options(many).size)
    }
}
