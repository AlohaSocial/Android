// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * A server's description of itself: `/api/v2/instance`, with `/api/v1/instance/` folded into the same
 * shape so one persisted type can be fed by either.
 *
 * @property streamingUrl null when there is no streaming; Nextcloud Social says so with an empty `urls`.
 * @property vapidKey `""` on Nextcloud Social, which is the server saying not to offer Web Push.
 * @property apiVersions `{"mastodon": 3}` on a 4.3-compatible server, read instead of a version string
 *   that says nothing on a fork.
 */
@Serializable
public data class InstanceDescription(
    val domain: String,
    val title: String = "",
    val version: String = "",
    val sourceUrl: String? = null,
    val shortDescription: String = "",
    val description: String = "",
    val thumbnail: String? = null,
    val languages: List<String> = emptyList(),
    val rules: List<InstanceRule> = emptyList(),
    val contactAccount: Account? = null,
    val contactEmail: String? = null,
    val registrationsEnabled: Boolean = false,
    val approvalRequired: Boolean = false,
    val userCount: Int? = null,
    val statusCount: Int? = null,
    val domainCount: Int? = null,
    val streamingUrl: String? = null,
    val vapidKey: String? = null,
    val translationEnabled: Boolean = false,
    val apiVersions: Map<String, Int> = emptyMap(),
    val limits: ServerLimits = ServerLimits.MastodonDefaults,
) {
    /** The Mastodon API generation this server implements, where it says. */
    val mastodonApiVersion: Int? get() = apiVersions["mastodon"]

    val hasStreaming: Boolean get() = streamingUrl != null

    /** An empty key is the server saying not to offer Web Push. */
    val hasWebPush: Boolean get() = !vapidKey.isNullOrEmpty()
}

@Serializable
public data class InstanceRule(val id: String, val text: String, val hint: String? = null)

/** A published page such as the privacy policy or the terms of service. */
@Serializable
public data class InstanceDocument(
    val content: String,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
)
