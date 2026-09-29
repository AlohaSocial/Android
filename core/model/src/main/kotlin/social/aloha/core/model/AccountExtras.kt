// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/*
 * The account-side surfaces Nextcloud Social serves beyond Mastodon's client API: authorized apps,
 * the portfolio page, migration, statistics and channels.
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

/** Where a portfolio picture was taken, when the post carried a place. */
@Serializable
public data class PortfolioPlace(val name: String? = null, val country: String? = null)

/** One picture on a portfolio page: a status, reduced to what the page shows. */
@Serializable
public data class PortfolioPost(
    val id: String,
    val content: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val url: String? = null,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    val place: PortfolioPlace? = null,
) {
    /** The first picture, which is what a portfolio shows. */
    val picture: MediaAttachment?
        get() = mediaAttachments.firstOrNull { it.type == AttachmentKind.Image } ?: mediaAttachments.firstOrNull()
}

@Serializable
public enum class PortfolioLayout(override val wire: String) : WireValue {
    Grid("grid"),
    Rows("rows"),
    ;

    public companion object {
        public fun fromWire(raw: String?): PortfolioLayout = entries.fromWire(raw, Grid)
    }
}

@Serializable
public enum class PortfolioSource(override val wire: String) : WireValue {
    Recent("recent"),
    Collection("collection"),
    ;

    public companion object {
        public fun fromWire(raw: String?): PortfolioSource = entries.fromWire(raw, Recent)
    }
}

/**
 * The viewer's own portfolio settings, as `GET/POST /api/v1.1/portfolio` exchange them.
 *
 * @property posts the pictures the page would show, where the server sends them along.
 */
@Serializable
public data class PortfolioSettings(
    val active: Boolean = false,
    val title: String = "",
    val intro: String = "",
    val layout: PortfolioLayout = PortfolioLayout.Grid,
    val source: PortfolioSource = PortfolioSource.Recent,
    val collectionId: String? = null,
    val showCaptions: Boolean = true,
    val showPlaces: Boolean = true,
    val showDates: Boolean = true,
    val showAvatar: Boolean = true,
    val url: String? = null,
    val posts: List<PortfolioPost> = emptyList(),
)

/** A published portfolio page, readable by anybody with the address. Absent switches mean shown. */
@Serializable
public data class PortfolioPage(
    val title: String = "",
    val intro: String? = null,
    val handle: String? = null,
    val avatar: String? = null,
    val layout: PortfolioLayout = PortfolioLayout.Grid,
    val showCaptions: Boolean = true,
    val showPlaces: Boolean = true,
    val showDates: Boolean = true,
    val showAvatar: Boolean = true,
    val posts: List<PortfolioPost> = emptyList(),
)
