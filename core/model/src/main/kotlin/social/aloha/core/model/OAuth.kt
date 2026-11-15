// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * The app's OAuth client on one server. `POST /api/v1/apps` always creates a new client on Nextcloud
 * Social, so a registration is made once per server on this device and reused for every account
 * there. The secret belongs in the token vault only.
 */
@Serializable
public data class ClientRegistration(val clientId: String, val clientSecret: String, val scopes: String) {
    override fun toString(): String = "ClientRegistration(clientId=$clientId, scopes=$scopes)"
}

/**
 * A bearer token, with the [refreshToken] that renews it and the seconds it has left ([expiresIn]) where
 * the server sent them. Aloha Social since 0.26.299 issues both with every account token: a token
 * lasts 365 days from its issue, one issued by a refresh 24 hours, and every refresh replaces both
 * tokens. Servers without refresh tokens (Mastodon, older Social) send neither, and a 401 there means
 * signing in again.
 */
@Serializable
public data class AccessToken(
    val value: String,
    val scope: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
) {
    override fun toString(): String = "AccessToken(scope=$scope)"
}

/**
 * `/oauth/userinfo`: who the token belongs to. Aloha Social answers it even for a brand-new
 * account whose `verify_credentials` still fails with a 500 because its avatar is not cached yet.
 */
@Serializable
public data class OAuthUserInfo(val subject: String, val preferredUsername: String?, val name: String?)
