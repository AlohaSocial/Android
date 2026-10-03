// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * An account as a moderator sees it: Mastodon's `Admin::Account`, served by Nextcloud Social at
 * `/api/v1/admin/accounts`. An account there is a Nextcloud user or a cached remote actor, so the
 * login fields Mastodon has (email, IP, locale, role) come back empty and are not modelled.
 *
 * @property domain null for a local account.
 * @property account the public account entity, which is what a row draws.
 */
@Serializable
public data class AdminAccount(
    val id: String,
    val username: String,
    val domain: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val confirmed: Boolean = true,
    val approved: Boolean = true,
    val disabled: Boolean = false,
    val silenced: Boolean = false,
    val suspended: Boolean = false,
    val sensitized: Boolean = false,
    val account: Account? = null,
) {
    val isLocal: Boolean get() = domain.isNullOrEmpty()

    /** The one word for the account's standing: the strongest thing true of it. */
    val standing: AdminStanding
        get() = when {
            suspended -> AdminStanding.Suspended
            silenced -> AdminStanding.Silenced
            sensitized -> AdminStanding.Sensitized
            else -> AdminStanding.Active
        }

    /** `@user@host` for a remote account, `@user` for a local one. */
    val handle: String get() = if (domain.isNullOrEmpty()) "@$username" else "@$username@$domain"
}

public enum class AdminStanding { Active, Silenced, Suspended, Sensitized }

/**
 * What the signed-in person may moderate: the `permissions` bitmask of the `role` Mastodon puts on
 * `verify_credentials`. A server that reports no role (Nextcloud Social so far) grants nothing here, so
 * the moderation console stays hidden there; the admin API itself still decides on every call.
 */
@Serializable
public data class ModeratorRole(val permissions: Long = 0) {
    private fun has(bit: Long) = (permissions and (ADMINISTRATOR or bit)) != 0L

    val reports: Boolean get() = has(MANAGE_REPORTS)

    val accounts: Boolean get() = has(MANAGE_USERS)

    val trends: Boolean get() = has(MANAGE_TAXONOMIES)

    val any: Boolean get() = reports || accounts || trends

    public companion object {
        /** Mastodon's `UserRole` flags: an administrator holds every permission. */
        public const val ADMINISTRATOR: Long = 1L shl 0
        public const val MANAGE_REPORTS: Long = 1L shl 4
        public const val MANAGE_TAXONOMIES: Long = 1L shl 8
        public const val MANAGE_USERS: Long = 1L shl 10
    }
}

/**
 * A hashtag as a moderator reviews it: Mastodon's `Admin::Tag`. Its [id] is the one the review routes
 * take; Nextcloud Social names a tag by itself, so there the id falls back to the name.
 */
@Serializable
public data class AdminTag(
    val id: String,
    val name: String,
    val uses: Int = 0,
    val accounts: Int = 0,
    val requiresReview: Boolean = false,
    val trendable: Boolean = true,
)

/**
 * A link as a moderator reviews it among the trends: Mastodon's admin trend link, a preview card with the
 * [id] its review routes take. Nextcloud Social names a link by its address, so there the id falls back
 * to the [url].
 */
@Serializable
public data class AdminLink(
    val id: String,
    val url: String,
    val title: String = "",
    val providerName: String = "",
    val requiresReview: Boolean = false,
)

/**
 * What a moderator can do to an account: the `type` of `POST /api/v1/admin/accounts/{id}/action`.
 * Nextcloud Social acts on these three.
 */
public enum class AdminAccountAction(override val wire: String) : WireValue {
    /** Records the decision and changes nothing. */
    None("none"),

    /** The account's posts stop reaching people who do not follow it. */
    Silence("silence"),

    /** Nothing of the account is delivered or shown. */
    Suspend("suspend"),
}

/** A report as a moderator sees it: Mastodon's `Admin::Report`. */
@Serializable
public data class AdminReport(
    val id: String,
    val actionTaken: Boolean = false,
    @Serializable(with = InstantSerializer::class) val actionTakenAt: Instant? = null,
    val category: String = "",
    val comment: String = "",
    val forwarded: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    /** Who reported. */
    val account: Account? = null,
    /** Who was reported. */
    val targetAccount: Account? = null,
    val assignedAccount: Account? = null,
    val actionTakenByAccount: Account? = null,
    val statuses: List<Status> = emptyList(),
) {
    val isAssigned: Boolean get() = assignedAccount != null
}

/**
 * One week of `/api/v1/instance/activity`. Every value arrives as a string. `logins` and
 * `registrations` are always zero on Nextcloud Social: an account there is a Nextcloud user, so no
 * registration exists to count.
 *
 * @property week Unix seconds at the Monday the week began.
 */
@Serializable
public data class InstanceActivityWeek(
    val week: Long,
    val statuses: Int = 0,
    val logins: Int = 0,
    val registrations: Int = 0,
) {
    val id: Long get() = week

    val startsAt: Instant get() = Instant.ofEpochSecond(week)
}

/**
 * A server this instance refuses, from `/api/v1/instance/domain_blocks`, which is empty unless the
 * administrator publishes the list.
 */
@Serializable
public data class PublicDomainBlock(
    val domain: String,
    val digest: String = "",
    val severity: String = "suspend",
    val comment: String = "",
) {
    val id: String get() = digest.ifEmpty { domain }
}
