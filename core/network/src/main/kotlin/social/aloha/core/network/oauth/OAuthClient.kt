// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.oauth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import social.aloha.core.model.AccessToken
import social.aloha.core.model.AuthorizationServerMetadata
import social.aloha.core.model.ClientRegistration
import social.aloha.core.model.OAuthUserInfo
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.RequestExecutor
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.StringListSerializer
import social.aloha.core.network.dto.AuthorizationServerMetadataDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.map
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * Registration, token exchange and revocation. Writes here are never retried: an authorisation code
 * is single-use, and a second exchange answers 401 `unknown client_id`.
 */
public class OAuthClient internal constructor(private val executor: RequestExecutor) {
    // A redirect is never followed: it would carry the code, the secret or the verifier to wherever it points.
    public constructor(http: OkHttpClient, rateLimiter: RateLimiter, ioDispatcher: CoroutineDispatcher) :
        this(RequestExecutor(http.newBuilder().followRedirects(false).build(), rateLimiter, ioDispatcher))

    /** RFC 8414 metadata published under [apiBase], or null where the server has none. */
    public suspend fun metadata(apiBase: HttpUrl): AuthorizationServerMetadata? {
        val url = apiBase.newBuilder().addPathSegments(".well-known/oauth-authorization-server").build()
        val request =
            request(
                Endpoint(url.encodedPath.trimStart('/'), authentication = Authentication.None),
                AuthorizationServerMetadataDto.serializer(),
            ) {
                it.toDomain()
            }
        return (executor.execute(request, url, authorization = "") as? ApiResult.Success)?.value?.decoded?.value
    }

    /** `POST /api/v1/apps` with the app's identity and its redirect URIs. */
    public suspend fun register(
        apiBase: HttpUrl,
        scopes: String = OAuthIdentity.SCOPES,
        redirectUris: String = OAuthIdentity.REGISTERED_REDIRECTS,
    ): ApiResult<ClientRegistration> {
        val endpoint = Endpoint(
            path = "api/v1/apps",
            method = HttpMethod.POST,
            body = Body.Form(
                listOf(
                    QueryItem("client_name", OAuthIdentity.CLIENT_NAME),
                    QueryItem("redirect_uris", redirectUris),
                    QueryItem("scopes", scopes),
                    QueryItem("website", OAuthIdentity.WEBSITE),
                ),
            ),
            authentication = Authentication.None,
        )
        val url = apiBase.newBuilder().addPathSegments(endpoint.path).build()
        return executor.execute(request(endpoint, ApplicationDto.serializer()) { it.toDomain(scopes) }, url, "")
            .map { it.decoded.value }
    }

    /**
     * Exchanges [code] for a token. No `scope` is sent: the code names the authorisation, and
     * Nextcloud Social ignores one anyway.
     */
    public suspend fun exchange(
        tokenEndpoint: HttpUrl,
        registration: ClientRegistration,
        pending: PendingAuthorization,
        code: String,
    ): ApiResult<AccessToken> {
        val fields = listOf(
            QueryItem("grant_type", "authorization_code"),
            QueryItem("code", code),
            QueryItem("client_id", registration.clientId),
            QueryItem("client_secret", registration.clientSecret),
            QueryItem("redirect_uri", pending.redirectUri),
            QueryItem("code_verifier", pending.verifier),
        )
        val endpoint =
            Endpoint(
                tokenEndpoint.pathOf(),
                HttpMethod.POST,
                body = Body.Form(fields),
                authentication = Authentication.None,
            )
        return executor.execute(request(endpoint, TokenDto.serializer()) { it.toDomain() }, tokenEndpoint, "")
            .map { it.decoded.value }
    }

    /** Revokes one token; the server answers 200 whether or not it knew it. Other devices stay signed in. */
    public suspend fun revoke(
        endpoints: OAuthEndpoints,
        registration: ClientRegistration,
        token: AccessToken,
    ): ApiResult<Unit> {
        val fields = listOf(
            QueryItem("client_id", registration.clientId),
            QueryItem("client_secret", registration.clientSecret),
            QueryItem("token", token.value),
        )
        val endpoint =
            Endpoint(
                endpoints.revocation.pathOf(),
                HttpMethod.POST,
                body = Body.Form(fields),
                authentication = Authentication.None,
            )
        return executor.execute(unitRequest(endpoint), endpoints.revocation, "").map { }
    }

    /** Who [token] belongs to; answers for a new account whose `verify_credentials` still fails. */
    public suspend fun userInfo(userinfoEndpoint: HttpUrl, token: AccessToken): ApiResult<OAuthUserInfo> {
        val endpoint = Endpoint(userinfoEndpoint.pathOf(), authentication = Authentication.Bearer)
        return executor.execute(
            request(endpoint, UserInfoDto.serializer()) {
                it.toDomain()
            },
            userinfoEndpoint,
            "Bearer ${token.value}",
        )
            .map { it.decoded.value }
    }

    private fun HttpUrl.pathOf(): String = encodedPath.trimStart('/')
}

@Serializable
internal data class ApplicationDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @Serializable(with = StringListSerializer::class) val scopes: List<String> = emptyList(),
    @Serializable(with = LenientUrlSerializer::class) val website: String? = null,
)

/** A server that does not echo the scopes is taken to have registered the ones [asked] for. */
internal fun ApplicationDto.toDomain(asked: String = OAuthIdentity.SCOPES) = ClientRegistration(
    clientId,
    clientSecret,
    scopes.joinToString(" ").ifEmpty { asked },
)

@Serializable
internal data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String = "",
)

internal fun TokenDto.toDomain() = AccessToken(accessToken, scope)

@Serializable
internal data class UserInfoDto(
    val sub: String,
    @SerialName("preferred_username") val preferredUsername: String? = null,
    val name: String? = null,
)

internal fun UserInfoDto.toDomain() = OAuthUserInfo(sub, preferredUsername, name)
