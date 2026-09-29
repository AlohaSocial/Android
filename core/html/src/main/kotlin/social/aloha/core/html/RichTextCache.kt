// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Mention
import social.aloha.core.model.Status
import social.aloha.core.model.StatusTag

/**
 * Parses statuses and remembers the result by content, so a row scrolled back into view is not parsed
 * again. Safe to call from any thread; the parse itself runs outside the lock, so two rows never wait
 * on each other. The least recently used entry goes once [capacity] is reached.
 */
public class RichTextCache(private val capacity: Int = DEFAULT_CAPACITY) {
    private data class Key(
        val content: String,
        val mentions: List<Mention>,
        val tags: List<StatusTag>,
        val emojis: List<CustomEmoji>,
    )

    private val entries = object : LinkedHashMap<Key, RichText>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RichText>?): Boolean = size > capacity
    }

    /** The body of what [status] shows: for a boost, the boosted post. */
    public fun richText(status: Status): RichText {
        val shown = status.displayed
        return cached(Key(shown.content, shown.mentions, shown.tags, shown.emojis))
    }

    /** The content warning, plain text on the wire but able to carry custom emoji. */
    public fun spoiler(status: Status): RichText =
        status.displayed.let { StatusHtmlParser.parseText(it.spoilerText, it.emojis) }

    public fun plainText(status: Status): String = richText(status).plainText

    public fun clear() {
        synchronized(entries) { entries.clear() }
    }

    private fun cached(key: Key): RichText {
        synchronized(entries) { entries[key] }?.let { return it }
        val parsed = StatusHtmlParser.parse(key.content, key.mentions, key.tags, key.emojis)
        synchronized(entries) { entries[key] = parsed }
        return parsed
    }

    private companion object {
        const val DEFAULT_CAPACITY = 2_000
        const val LOAD_FACTOR = 0.75f
    }
}
