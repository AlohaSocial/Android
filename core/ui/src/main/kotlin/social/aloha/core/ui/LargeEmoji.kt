// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import java.text.BreakIterator
import social.aloha.core.html.RichText

/** How many times the body's size a post of only a few emoji is drawn at. */
internal const val LARGE_EMOJI_SCALE = 2.5f

/** The most emoji a post may hold and still be drawn large. */
private const val MAX_LARGE_EMOJI = 3

/**
 * Whether this text is nothing but one to three emoji, whitespace aside. A custom emoji of the post counts as
 * one; each other user-perceived character must be an emoji: a flag, a keycap, one with a skin tone, a ZWJ or
 * tag sequence.
 */
internal fun RichText.isFewEmoji(): Boolean {
    var count = 0
    for (run in runs) {
        count += (if (run.emoji != null) 1 else emojiCount(run.text)) ?: (MAX_LARGE_EMOJI + 1)
        if (count > MAX_LARGE_EMOJI) return false
    }
    return count > 0
}

/** How many emoji [text] holds, whitespace aside, counted up to one past the most; null where it holds other text. */
internal fun emojiCount(text: String): Int? {
    val clusters = graphemes(text).filter { it.isNotBlank() }.take(MAX_LARGE_EMOJI + 1).toList()
    return clusters.size.takeIf { clusters.all(::isEmoji) }
}

/** The user-perceived characters of [text], one at a time. */
private fun graphemes(text: String): Sequence<String> {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    return generateSequence(iterator.first() to iterator.next()) { (_, end) -> end to iterator.next() }
        .takeWhile { (_, end) -> end != BreakIterator.DONE }
        .map { (start, end) -> text.substring(start, end) }
}

/**
 * Whether one user-perceived character is an emoji: it asks for emoji presentation, is a keycap or carries a
 * skin tone, or starts with a regional indicator or a character drawn as an emoji by default.
 */
private fun isEmoji(cluster: String): Boolean {
    val first = cluster.codePointAt(0)
    return cluster.codePoints().anyMatch { it == EMOJI_PRESENTATION || it == KEYCAP || it in SKIN_TONES } ||
        first in PICTOGRAPHS ||
        EMOJI_BY_DEFAULT.any { first in it }
}

private const val EMOJI_PRESENTATION = 0xFE0F
private const val KEYCAP = 0x20E3
private val SKIN_TONES = 0x1F3FB..0x1F3FF

/** Regional indicators, the playing and mahjong tiles, the enclosed letters and every pictograph block above them. */
private val PICTOGRAPHS = 0x1F000..0x1FAFF

/** The characters below the pictograph blocks that are drawn as emoji without asking (Emoji_Presentation). */
private val EMOJI_BY_DEFAULT = listOf(
    0x231A..0x231B, 0x23E9..0x23EC, 0x23F0..0x23F0, 0x23F3..0x23F3, 0x25FD..0x25FE, 0x2614..0x2615,
    0x2648..0x2653, 0x267F..0x267F, 0x2693..0x2693, 0x26A1..0x26A1, 0x26AA..0x26AB, 0x26BD..0x26BE,
    0x26C4..0x26C5, 0x26CE..0x26CE, 0x26D4..0x26D4, 0x26EA..0x26EA, 0x26F2..0x26F3, 0x26F5..0x26F5,
    0x26FA..0x26FA, 0x26FD..0x26FD, 0x2705..0x2705, 0x270A..0x270B, 0x2728..0x2728, 0x274C..0x274C,
    0x274E..0x274E, 0x2753..0x2755, 0x2757..0x2757, 0x2795..0x2797, 0x27B0..0x27B0, 0x27BF..0x27BF,
    0x2B1B..0x2B1C, 0x2B50..0x2B50, 0x2B55..0x2B55,
)
