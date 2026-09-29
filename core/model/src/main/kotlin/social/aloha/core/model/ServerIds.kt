// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * Orders server ids. Ids are decimal strings that exceed 2^53 (Nextcloud Social's are
 * `unix_ms × 10^6 + 6 random digits`), so they are compared by length and then lexically, and never
 * parsed into a number. Non-numeric ids compare the same way, which keeps the order total.
 */
public object ServerIds : Comparator<String> {
    override fun compare(a: String, b: String): Int =
        if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)

    public fun isNewer(candidate: String, than: String): Boolean = compare(candidate, than) > 0

    public fun newest(ids: Iterable<String>): String? = ids.maxWithOrNull(this)
}
