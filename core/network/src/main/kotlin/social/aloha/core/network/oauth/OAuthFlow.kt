// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.oauth

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.model.AuthorizationServerMetadata
import social.aloha.core.model.NodeInfo
import social.aloha.core.network.sameOrigin

/**
 * The identity the app registers with; the same on every platform, so a person's list of authorised
 * apps stays tidy.
 */
public object OAuthIdentity {
    public const val CLIENT_NAME: String = "Aloha Social"
    public const val WEBSITE: String = "https://aloha.social"

    /** Coarse grants covering every granular scope Nextcloud Social checks; `push` is needed where Web Push exists. */
    public const val SCOPES: String = "read write follow push"

    /**
     * What a moderator's second authorisation asks for, and what the app registers before it: a server
     * refuses an authorisation for more than the app was registered with. Ordinary sign-in never asks for
     * the admin scopes, so no one else sees them on the consent page.
     */
    public const val MODERATOR_SCOPES: String = "$SCOPES admin:read admin:write"

    /** The verified App Link: no other app can receive a code sent here. */
    public const val APP_LINK_REDIRECT: String = "https://aloha.social/oauth/callback"

    /** The fallback when link verification failed on this device; PKCE makes a hijacked code useless. */
    public const val SCHEME_REDIRECT: String = "alohasocial://oauth-callback"

    /** Both are registered, one per line; Nextcloud Social splits `redirect_uris` on newlines. */
    public const val REGISTERED_REDIRECTS: String = "$APP_LINK_REDIRECT\n$SCHEME_REDIRECT"
}

/** Where a server's OAuth endpoints are. */
public data class OAuthEndpoints(
    val authorization: HttpUrl,
    val token: HttpUrl,
    val revocation: HttpUrl,
    val userinfo: HttpUrl,
) {
    public companion object {
        /** The conventional paths under the API base, used where the server publishes no metadata. */
        public fun conventional(apiBase: HttpUrl): OAuthEndpoints = OAuthEndpoints(
            authorization = apiBase.child("oauth/authorize"),
            token = apiBase.child("oauth/token"),
            revocation = apiBase.child("oauth/revoke"),
            userinfo = apiBase.child("oauth/userinfo"),
        )

        /**
         * The endpoints the metadata names, each accepted only on the API base's own origin, so a
         * hostile document cannot send the code or the client secret elsewhere.
         */
        public fun from(metadata: AuthorizationServerMetadata?, apiBase: HttpUrl): OAuthEndpoints {
            val fallback = conventional(apiBase)
            fun pick(raw: String?, default: HttpUrl): HttpUrl =
                raw?.toHttpUrlOrNull()?.takeIf { it.sameOrigin(apiBase) } ?: default
            return OAuthEndpoints(
                authorization = pick(metadata?.authorizationEndpoint, fallback.authorization),
                token = pick(metadata?.tokenEndpoint, fallback.token),
                revocation = pick(metadata?.revocationEndpoint, fallback.revocation),
                userinfo = pick(metadata?.userinfoEndpoint, fallback.userinfo),
            )
        }

        private fun HttpUrl.child(path: String): HttpUrl = newBuilder().addPathSegments(path).build()
    }
}

/**
 * One sign-in attempt in flight: what has to survive while the browser tab is open, a process death
 * included, so the callback can be matched and the code exchanged.
 *
 * @property authorizationUrl the page opened in the browser, so it can be opened again.
 * @property tokenEndpoint where the code is exchanged, fixed when the attempt began.
 * @property nodeInfo what the probe learned about the software, which capability detection needs.
 */
@Serializable
public data class PendingAuthorization(
    val apiBase: String,
    val state: String,
    val verifier: String,
    val redirectUri: String,
    val authorizationUrl: String,
    val tokenEndpoint: String,
    val userinfoEndpoint: String,
    val nodeInfo: NodeInfo? = null,
) {
    override fun toString(): String = "PendingAuthorization(apiBase=$apiBase)"
}

private const val STATE_BYTES = 32

/** Builds the authorisation URL and the [PendingAuthorization] that belongs to it. */
public fun authorizationUrl(
    endpoints: OAuthEndpoints,
    apiBase: HttpUrl,
    clientId: String,
    redirectUri: String,
    pkce: Pkce = Pkce.generate(),
    state: String = Pkce.randomToken(STATE_BYTES),
    scope: String = OAuthIdentity.SCOPES,
): Pair<HttpUrl, PendingAuthorization> {
    val url = endpoints.authorization.newBuilder()
        .addQueryParameter("response_type", "code")
        .addQueryParameter("client_id", clientId)
        .addQueryParameter("redirect_uri", redirectUri)
        .addQueryParameter("scope", scope)
        .addQueryParameter("state", state)
        .addQueryParameter("code_challenge", pkce.challenge)
        .addQueryParameter("code_challenge_method", pkce.method)
        .build()
    val pending = PendingAuthorization(
        apiBase.toString(),
        state,
        pkce.verifier,
        redirectUri,
        authorizationUrl = url.toString(),
        tokenEndpoint = endpoints.token.toString(),
        userinfoEndpoint = endpoints.userinfo.toString(),
    )
    return url to pending
}

/** What an OAuth callback said. */
public sealed interface OAuthCallback {
    public data class Code(val code: String) : OAuthCallback {
        override fun toString(): String = "Code(redacted)"
    }

    /** The person or the server refused; [reason] is the server's own wording. */
    public data class Denied(val reason: String?) : OAuthCallback

    /** The state did not match the attempt in flight: the code is dropped, never exchanged. */
    public data object StateMismatch : OAuthCallback

    public data object MissingCode : OAuthCallback

    public companion object {
        /**
         * Reads a callback URI. The server appends its parameters to whatever the registered URI
         * had, and adds a `/` to the scheme redirect, so that one arrives as
         * `alohasocial://oauth-callback/?code=…&state=…`.
         */
        public fun parse(uri: String, expectedState: String): OAuthCallback {
            // A custom-scheme URI is not an HttpUrl; its query is, so it is parsed on a placeholder origin.
            val query = uri.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
            val parameters = "https://callback.invalid/?$query".toHttpUrlOrNull() ?: return MissingCode
            val error = parameters.queryParameter("error")
            val code = parameters.queryParameter("code")?.takeIf { it.isNotEmpty() }
            return when {
                parameters.queryParameter("state") != expectedState -> StateMismatch
                error != null -> Denied(parameters.queryParameter("error_description") ?: error)
                code != null -> Code(code)
                else -> MissingCode
            }
        }
    }
}
