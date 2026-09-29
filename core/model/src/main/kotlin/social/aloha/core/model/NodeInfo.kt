// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * NodeInfo 2.x, which names the server software. Nextcloud Social serves it at the domain root
 * whether or not the client-API rewrite rules are installed, which makes it the cross-check for
 * API-base discovery.
 */
@Serializable
public data class NodeInfo(
    val version: String,
    val softwareName: String,
    val softwareVersion: String,
    val repository: String? = null,
    val homepage: String? = null,
    val protocols: List<String> = emptyList(),
    val openRegistrations: Boolean = false,
) {
    /** Nextcloud Social identifies as `nextcloud-social`; older releases as `Nextcloud Social`. */
    val isNextcloudSocial: Boolean
        get() = softwareName.lowercase().let { it.contains("nextcloud") && it.contains("social") }
}

/**
 * RFC 8414 authorization server metadata (`/.well-known/oauth-authorization-server`). On Nextcloud
 * Social the [issuer] and every endpoint name the app path, with or without the root rewrite rules.
 */
@Serializable
public data class AuthorizationServerMetadata(
    val issuer: String,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val revocationEndpoint: String? = null,
    val userinfoEndpoint: String? = null,
    val codeChallengeMethods: List<String>? = null,
    val scopes: List<String>? = null,
) {
    /** PKCE with S256 is assumed where the server does not say; it is the only method sent. */
    val supportsPkceS256: Boolean get() = codeChallengeMethods?.contains("S256") ?: true
}
