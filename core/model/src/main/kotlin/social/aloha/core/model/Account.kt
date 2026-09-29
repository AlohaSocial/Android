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
    val header: String? = null,
    val headerStatic: String? = null,
    val locked: Boolean = false,
    val bot: Boolean = false,
    val discoverable: Boolean = true,
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
) {
    /** What a person reads; never empty, it falls back through the handle. */
    val bestDisplayName: String
        get() = displayName.ifEmpty { username.ifEmpty { acct } }

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
)
