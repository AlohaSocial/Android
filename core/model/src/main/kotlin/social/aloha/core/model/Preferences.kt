// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * The account's posting and reading preferences.
 *
 * @property expandMedia real on Nextcloud Social, with PeerTube's three NSFW policies under Mastodon's
 *   names. A client that cannot read it guesses, and posts publicly for someone whose default is
 *   followers-only.
 */
@Serializable
public data class Preferences(
    val defaultVisibility: Visibility? = null,
    val defaultSensitive: Boolean = false,
    val defaultLanguage: String? = null,
    val expandMedia: SensitiveMediaPolicy? = null,
    val expandSpoilers: Boolean = false,
)

/** What happens to notifications from each kind of stranger. Missing decisions are `accept`. */
@Serializable
public data class NotificationPolicy(
    val forNotFollowing: PolicyDecision = PolicyDecision.Accept,
    val forNotFollowers: PolicyDecision = PolicyDecision.Accept,
    val forNewAccounts: PolicyDecision = PolicyDecision.Accept,
    val forPrivateMentions: PolicyDecision = PolicyDecision.Accept,
    val forLimitedAccounts: PolicyDecision = PolicyDecision.Accept,
    val summary: NotificationPolicySummary? = null,
)

@Serializable
public enum class PolicyDecision(override val wire: String) : WireValue {
    Accept("accept"),
    Filter("filter"),
    Drop("drop"),
    Unknown("__unknown"),
    ;

    public companion object {
        /** A missing decision is `accept`. */
        public fun fromWire(raw: String?): PolicyDecision = if (raw == null) Accept else entries.fromWire(raw, Unknown)
    }
}

@Serializable
public data class NotificationPolicySummary(val pendingRequestsCount: Int = 0, val pendingNotificationsCount: Int = 0)

/** Notifications held back from one account by the policy. */
@Serializable
public data class NotificationRequest(
    val id: String,
    val account: Account,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    val notificationsCount: Int = 0,
    val lastStatus: Status? = null,
)
