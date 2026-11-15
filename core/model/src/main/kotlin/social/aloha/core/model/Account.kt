// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * An account on this or another server.
 *
 * @property acct `alice` for a local account, `alice@example.social` for a remote one.
 * @property avatar none when the server sends `""` or `null`, which Nextcloud Social does for an
 *   account whose avatar it has not cached yet.
 * @property avatarSmall the same picture at most 128 px across, where the server keeps one (Nextcloud Social
 *   0.26.296 and later, for a remote account's cached avatar); none elsewhere.
 * @property avatarDefault the avatar is the server's stand-in, not a picture the account chose
 *   (Nextcloud Social says so; other servers do not, and it stays false there).
 * @property header none as well when it is only the server's stand-in for "no header".
 * @property source present only on the credentials routes, which answer for the account itself.
 */
@Serializable
public data class Account(
    val id: String,
    val username: String,
    val acct: String,
    val displayName: String = "",
    val note: String = "",
    val url: String? = null,
    val uri: String? = null,
    val avatar: String? = null,
    val avatarStatic: String? = null,
    val avatarSmall: String? = null,
    val avatarDefault: Boolean = false,
    val header: String? = null,
    val headerStatic: String? = null,
    val locked: Boolean = false,
    val bot: Boolean = false,
    val discoverable: Boolean = true,
    /** Whether search engines may include the account's public posts, as its owner allowed. */
    val indexable: Boolean = false,
    val suspended: Boolean = false,
    val limited: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val statusesCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastStatusAt: Instant? = null,
    val fields: List<AccountField> = emptyList(),
    val emojis: List<CustomEmoji> = emptyList(),
    val source: AccountSource? = null,
    val moved: Account? = null,
    /** Kept in memory of someone who died; nobody posts from it any more. */
    val memorial: Boolean = false,
    /** How the account wants its profile shown, where its server says (Nextcloud Social); null shows it all. */
    val profileLayout: ProfileLayout? = null,
    /**
     * Whether the account shows its followers and following to nobody else (`hide_collections`); null
     * where the server does not say, which Nextcloud Social does for an account embedded in another entity.
     */
    val hideCollections: Boolean? = null,
) {
    /** What a person reads; never empty, it falls back through the handle. */
    val bestDisplayName: String
        get() = displayName.ifEmpty { username.ifEmpty { acct } }

    /** The picture to draw at 48 dp and less: [avatarSmall] where the server sent one, else [avatar]. */
    val smallAvatar: String? get() = avatarSmall ?: avatar

    /** The handle as a person writes it, with a leading `@`. */
    val qualifiedHandle: String get() = "@$acct"

    /** The account's server: the part of [acct] after `@`, else the host of [url]. */
    val host: String?
        get() {
            val at = acct.indexOf('@')
            if (at >= 0) return acct.substring(at + 1)
            return hostOf(url)
        }
}

/**
 * The tab an account's profile opens on and the tabs it offers, as Nextcloud Social names them (`posts`,
 * `photos`, `videos`, `tagged`, `collections`). How the profile looks, never who may see what: a tab left
 * out is still there to anyone who asks for it.
 */
@Serializable
public data class ProfileLayout(val defaultTab: String = POSTS, val tabs: List<String> = ALL) {
    public fun shows(tab: String): Boolean = tab in tabs

    public companion object {
        public const val POSTS: String = "posts"
        public const val PHOTOS: String = "photos"
        public const val VIDEOS: String = "videos"
        public const val COLLECTIONS: String = "collections"
        public val ALL: List<String> = listOf(POSTS, PHOTOS, VIDEOS, "tagged", COLLECTIONS)
    }
}

@Serializable
public data class AccountField(
    val name: String,
    val value: String,
    @Serializable(with = InstantSerializer::class) val verifiedAt: Instant? = null,
) {
    val isVerified: Boolean get() = verifiedAt != null
}

@Serializable
public data class AccountSource(
    val note: String? = null,
    val fields: List<AccountField>? = null,
    val privacy: Visibility? = null,
    val sensitive: Boolean = false,
    val language: String? = null,
    val followRequestsCount: Int = 0,
    /** Whether the account hides its followers and following; null from a server without the setting. */
    val hideCollections: Boolean? = null,
)
