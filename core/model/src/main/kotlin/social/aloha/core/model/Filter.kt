// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * A Mastodon 4.x v2 filter. Nextcloud Social applies filters on the server; the app applies them
 * again locally, so a filter takes effect over cached content at once and stops when it expires.
 */
@Serializable
public data class Filter(
    val id: String,
    val title: String = "",
    val context: List<FilterContext> = emptyList(),
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
    val filterAction: FilterAction = FilterAction.Warn,
    val keywords: List<FilterKeyword> = emptyList(),
    val statuses: List<FilterStatus> = emptyList(),
) {
    public fun isExpired(now: Instant): Boolean = expiresAt?.let { !it.isAfter(now) } ?: false
}

@Serializable
public data class FilterKeyword(val id: String, val keyword: String, val wholeWord: Boolean = false)

@Serializable
public data class FilterStatus(val id: String, val statusId: String)
