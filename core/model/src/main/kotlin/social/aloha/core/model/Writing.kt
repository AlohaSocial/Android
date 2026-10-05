// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * How the reader writes, as chosen in Settings, Writing.
 *
 * @param numberThreads each part of a thread ends in its number, `1/3`, counted in its length.
 * @param visibility posts start out with it instead of the server's default; null for the server's.
 * @param language posts start out in it instead of the server's default; null for the server's.
 * @param quoteUnlistedNoted the writer was told, once, what quoting a quiet public post does.
 * @param askBeforeFollowersQuote quoting someone else's followers-only post asks first.
 */
public data class Writing(
    val confirmBeforePosting: Boolean = false,
    val alwaysShowWarning: Boolean = false,
    val numberThreads: Boolean = false,
    val visibility: Visibility? = null,
    val language: String? = null,
    val quoteUnlistedNoted: Boolean = false,
    val askBeforeFollowersQuote: Boolean = true,
) {
    public companion object {
        /** The number a part of a thread of [size] ends in, from its [index]; nothing for a single post. */
        public fun numbering(index: Int, size: Int): String = if (size > 1) "\n\n${index + 1}/$size" else ""
    }
}
