// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The `Link` header, the only cursor the app reads. Every filter of the original request survives
 * into both links, so a page is fetched by following the URL verbatim, never by rebuilding it.
 * Aloha Social points the links at the app path even when the request came in through the root
 * rules.
 */
public data class LinkHeader(val next: HttpUrl? = null, val previous: HttpUrl? = null) {
    val isEmpty: Boolean get() = next == null && previous == null

    public companion object {
        public fun parse(raw: String?): LinkHeader {
            if (raw.isNullOrBlank()) return LinkHeader()
            val links = splitEntries(raw).mapNotNull(::link).toMap()
            return LinkHeader(next = links["next"], previous = links["prev"] ?: links["previous"])
        }

        /** One `<url>; rel="x"` entry as relation to URL, or null when it is malformed. */
        private fun link(entry: String): Pair<String, HttpUrl>? {
            val match = ENTRY.find(entry) ?: return null
            val url = match.groupValues[1].trim().toHttpUrlOrNull() ?: return null
            return relation(match.groupValues[2])?.let { it to url }
        }

        private val ENTRY = Regex("<([^>]*)>(.*)")

        /** Entries are comma-separated, but a URL may contain a comma, so only commas outside `<…>` split. */
        private fun splitEntries(raw: String): List<String> {
            val entries = mutableListOf<String>()
            val current = StringBuilder()
            var insideBrackets = false
            for (character in raw) {
                when {
                    character == '<' -> insideBrackets = true
                    character == '>' -> insideBrackets = false
                }
                if (character == ',' && !insideBrackets) {
                    entries += current.toString()
                    current.clear()
                } else {
                    current.append(character)
                }
            }
            if (current.isNotBlank()) entries += current.toString()
            return entries
        }

        private fun relation(parameters: String): String? = parameters.split(';')
            .map { it.trim() }
            .firstOrNull { it.lowercase().startsWith("rel") && it.contains('=') }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"', '\'')
            ?.lowercase()
    }
}

/**
 * An answer that is not a page, and whether the server is still reading for it in the background, so the
 * same request made again a few seconds later brings more.
 *
 * @property filling a post's counts are still being read from its origin (Aloha Social's
 *   `X-Social-Filling: 1`).
 * @property threadFilling a conversation's replies are still being read from where they live (Nextcloud
 *   Social's `X-Social-Thread-Filling: 1`, on `/context`).
 */
public data class Filled<T>(val value: T, val filling: Boolean = false, val threadFilling: Boolean = false)

/**
 * A decoded page and its cursors.
 *
 * @property rawCount how many rows the server sent before lossy decoding dropped any, which is what
 *   decides "a page shorter than the limit is the last one" on routes that send no `Link` header.
 * @property filling the server is still reading for this page in the background, counts or posts, and the
 *   same page read again a little later brings them (Aloha Social's `X-Social-Filling: 1`).
 * @property profileFilling an account's older posts are still being read from its server, so a short page
 *   is not yet the end (Aloha Social's `X-Social-Profile-Filling: 1`).
 * @property profileSyncFailed reading an account's posts from its server failed, so the page holds only what
 *   the reader's server had (Aloha Social's `X-Social-Profile-Sync-State: failed`).
 */
public data class Paginated<T>(
    val items: List<T>,
    val link: LinkHeader,
    val rawCount: Int,
    val requestedLimit: Int,
    val filling: Boolean = false,
    val profileFilling: Boolean = false,
    val profileSyncFailed: Boolean = false,
) {
    /** Trust the header first; fall back to the count only where the server sends no header (`/blocks`, `/mutes`). */
    val mayHaveMore: Boolean get() = if (!link.isEmpty) link.next != null else rawCount >= requestedLimit
}
