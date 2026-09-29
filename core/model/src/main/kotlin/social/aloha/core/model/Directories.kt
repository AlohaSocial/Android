// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/*
 * Nextcloud Social's discovery surfaces: starter packs, other servers' directories, the follow
 * graph and Pixelfed's discover categories. All optional, and the routes most likely to change shape
 * between server versions.
 */

/**
 * A curated bundle of accounts to follow in one go.
 *
 * @property handles the handles the pack names, before resolution.
 * @property size how many accounts the pack holds; the list route sends the number without resolving anybody.
 * @property accounts resolved on the single-pack route only.
 */
@Serializable
public data class StarterPack(
    val slug: String,
    val name: String,
    val description: String = "",
    val handles: List<String> = emptyList(),
    val size: Int = 0,
    val accounts: List<Account> = emptyList(),
) {
    val id: String get() = slug
}

/** One directory the server asks when searching beyond itself. */
@Serializable
public data class DirectorySource(val host: String, val kind: String = "", val label: String = host) {
    val id: String get() = host
}

/** What one source said when asked: how many it gave, or why nothing. */
@Serializable
public data class DirectorySourceReport(
    val host: String,
    val status: String? = null,
    val error: String? = null,
    val count: Int = 0,
) {
    val id: String get() = host
}

/** `GET /api/v1/directories/search`: people found across the sources. */
@Serializable
public data class DirectorySearchResults(
    val accounts: List<Account> = emptyList(),
    val sources: List<DirectorySourceReport> = emptyList(),
)

/** `GET /api/v1/directories/hashtags`: what other servers discuss. */
@Serializable
public data class DirectoryHashtagResults(
    val hashtags: List<Tag> = emptyList(),
    val sources: List<DirectorySourceReport> = emptyList(),
)

/**
 * Somebody the people the reader follows also follow.
 *
 * @property count how many of the reader's follows follow this account.
 * @property via a few of the people who do, where the server names them.
 */
@Serializable
public data class FollowGraphSuggestion(
    val account: Account,
    val count: Int = 0,
    val via: List<Account> = emptyList(),
) {
    val id: String get() = account.id
}

/**
 * `GET /api/v1/follow_graph`.
 *
 * @property asked how many of the reader's follows the server asked.
 * @property needs how many follows a walk needs before it is worth anything.
 */
@Serializable
public data class FollowGraph(
    val suggestions: List<FollowGraphSuggestion> = emptyList(),
    val asked: Int = 0,
    val needs: Int = 0,
)

/** `GET /api/v1/follow_graph/status`: whether a walk is worth the requests. */
@Serializable
public data class FollowGraphStatus(val isWorthwhile: Boolean, val following: Int = 0, val needs: Int = 0)

/** A curated subject with the hashtags, normalised, that make it up. */
@Serializable
public data class DiscoverCategory(val id: String, val name: String, val hashtags: List<String> = emptyList())
