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
 * A bearer token. There is no refresh token: a token lives about a year from its last use, and a 401
 * means signing in again.
 */
@Serializable
public data class AccessToken(val value: String, val scope: String) {
    override fun toString(): String = "AccessToken(scope=$scope)"
}

/**
 * `/oauth/userinfo`: who the token belongs to. Nextcloud Social answers it even for a brand-new
 * account whose `verify_credentials` still fails with a 500 because its avatar is not cached yet.
 */
@Serializable
public data class OAuthUserInfo(val subject: String, val preferredUsername: String?, val name: String?)
