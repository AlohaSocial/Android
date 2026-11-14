// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/*
 * The account-side surfaces Nextcloud Social serves beyond Mastodon's client API: authorized apps,
 * migration, statistics and channels.
 */

/** One application that holds a token for the viewer's account. */
@Serializable
public data class AuthorizedApp(
    val id: String,
    val name: String = "",
    val website: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val signedIn: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastUsedAt: Instant? = null,
    val scopes: List<String> = emptyList(),
)

/** What a video belongs to everywhere but here: PeerTube's `Group`. */
@Serializable
public data class VideoChannel(
    val id: String,
    val handle: String = "",
    val name: String = "",
    val description: String = "",
    val url: String? = null,
    val videosCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
)
