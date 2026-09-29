// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.oauth

import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import social.aloha.core.model.AccessToken
import social.aloha.core.model.AuthorizationServerMetadata
import social.aloha.core.model.ClientRegistration
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.RateLimiter

class OAuthTest {
    private val server = MockWebServer()
    private lateinit var base: HttpUrl
    private val client by lazy { OAuthClient(OkHttpClient(), RateLimiter(nowMillis = { 0L }), Dispatchers.IO) }
    private val registration = ClientRegistration("client-1", "secret-1", "read write follow push")

    @BeforeEach
    fun start() {
        server.start()
        base = server.url("/index.php/apps/social/")
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun pending(verifier: String) =
        authorizationUrl(OAuthEndpoints.conventional(base), base, "client-1", OAuthIdentity.SCHEME_REDIRECT).second
            .copy(state = "s1", verifier = verifier)

    private fun respond(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun formOf(body: String?): Map<String, String> =
        "https://form.invalid/?${body.orEmpty()}".toHttpUrl().let { url ->
            url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() }
        }

    @Test
    fun `the PKCE verifier is 86 unreserved characters and the challenge is its unpadded SHA-256`() {
        val pkce = Pkce.generate()
        assertEquals(86, pkce.verifier.length)
        assertTrue(pkce.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        val expected = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray()))
        assertEquals(expected, pkce.challenge)
        assertFalse(pkce.challenge.contains('='))
    }

    @Test
    fun `the authorisation URL carries state, scopes and an S256 challenge`() {
        val pkce = Pkce("verifier-".repeat(6))
        val (url, pending) = authorizationUrl(
            OAuthEndpoints.conventional(base),
            base,
            clientId = "client-1",
            redirectUri = OAuthIdentity.SCHEME_REDIRECT,
            pkce = pkce,
            state = "state-1",
        )
        assertEquals("/index.php/apps/social/oauth/authorize", url.encodedPath)
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("state-1", url.queryParameter("state"))
        assertEquals("read write follow push", url.queryParameter("scope"))
        assertEquals(pkce.challenge, url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals(pkce.verifier, pending.verifier)
        assertFalse(pending.toString().contains(pkce.verifier))
    }

    @Test
    fun `a scheme callback with the server's added slash yields the code`() {
        assertEquals(
            OAuthCallback.Code("abc"),
            OAuthCallback.parse("alohasocial://oauth-callback/?code=abc&state=s1", expectedState = "s1"),
        )
        assertEquals(
            OAuthCallback.Code("abc"),
            OAuthCallback.parse("https://aloha.social/oauth/callback?x=1&code=abc&state=s1#frag", "s1"),
        )
    }

    @Test
    fun `a callback for another state is dropped`() {
        assertEquals(
            OAuthCallback.StateMismatch,
            OAuthCallback.parse("alohasocial://oauth-callback/?code=abc&state=evil", "s1"),
        )
        assertEquals(OAuthCallback.StateMismatch, OAuthCallback.parse("alohasocial://oauth-callback/?code=abc", "s1"))
    }

    @Test
    fun `a denial is a denial, not a missing code`() {
        assertEquals(
            OAuthCallback.Denied("The user denied access"),
            OAuthCallback.parse(
                "alohasocial://oauth-callback/?error=access_denied" +
                    "&error_description=The%20user%20denied%20access&state=s1",
                "s1",
            ),
        )
        // someone else's denial is not shown as ours
        assertEquals(
            OAuthCallback.StateMismatch,
            OAuthCallback.parse("alohasocial://oauth-callback/?error=access_denied", "s1"),
        )
        assertEquals(OAuthCallback.MissingCode, OAuthCallback.parse("alohasocial://oauth-callback/?state=s1", "s1"))
    }

    @Test
    fun `metadata endpoints on another origin are ignored in favour of the conventional ones`() {
        val metadata = AuthorizationServerMetadata(
            issuer = base.toString(),
            authorizationEndpoint = "${base}oauth/authorize",
            tokenEndpoint = "https://attacker.example/token",
        )
        val endpoints = OAuthEndpoints.from(metadata, base)
        assertEquals(base.host, endpoints.token.host)
        assertEquals("/index.php/apps/social/oauth/token", endpoints.token.encodedPath)
    }

    @Test
    fun `registration sends the identity and both redirect URIs, one per line`() = runTest {
        respond("""{"id":"2","client_id":"c","client_secret":"s","scopes":"read write follow push","vapid_key":""}""")
        val result = client.register(base)
        assertEquals(ApiResult.Success(ClientRegistration("c", "s", "read write follow push")), result)
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("/index.php/apps/social/api/v1/apps", recorded?.url?.encodedPath)
        val form = formOf(recorded?.body?.utf8())
        assertEquals("Aloha Social", form["client_name"])
        assertEquals("https://aloha.social/oauth/callback\nalohasocial://oauth-callback", form["redirect_uris"])
        assertEquals("https://aloha.social", form["website"])
        assertNull(recorded?.headers?.get("Authorization"))
    }

    @Test
    fun `a Mastodon registration with scopes as an array decodes`() = runTest {
        respond("""{"client_id":"c","client_secret":"s","scopes":["read","write"]}""")
        assertEquals("read write", (client.register(base) as ApiResult.Success).value.scopes)
    }

    @Test
    fun `the exchange sends the verifier and redirect, and no scope`() = runTest {
        respond("""{"access_token":"t","token_type":"Bearer","scope":"read write follow push","created_at":1}""")
        val pending = pending(verifier = "verifier-1")
        val result = client.exchange(OAuthEndpoints.conventional(base).token, registration, pending, code = "code-1")
        assertEquals(ApiResult.Success(AccessToken("t", "read write follow push")), result)
        val form = formOf(server.takeRequest(1, TimeUnit.SECONDS)?.body?.utf8())
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("verifier-1", form["code_verifier"])
        assertEquals("alohasocial://oauth-callback", form["redirect_uri"])
        assertEquals("secret-1", form["client_secret"])
        assertFalse(form.containsKey("scope"))
    }

    @Test
    fun `a spent code is an unauthorised answer, never retried`() = runTest {
        respond("""{"error":"unknown client_id"}""", code = 401)
        val pending = pending(verifier = "v")
        val result = client.exchange(OAuthEndpoints.conventional(base).token, registration, pending, "used")
        assertEquals(ApiResult.Failure(ApiError.Unauthorised("unknown client_id")), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `revocation posts the token with the client credentials`() = runTest {
        respond("{}")
        assertEquals(
            ApiResult.Success(Unit),
            client.revoke(OAuthEndpoints.conventional(base), registration, AccessToken("t", "")),
        )
        val form = formOf(server.takeRequest(1, TimeUnit.SECONDS)?.body?.utf8())
        assertEquals("t", form["token"])
        assertEquals("client-1", form["client_id"])
    }

    @Test
    fun `userinfo is fetched with the new token`() = runTest {
        respond(
            """{"sub":"http://nextcloud.local/index.php/apps/social/@alice",""" +
                """"name":"alice","preferred_username":"alice"}""",
        )
        val info = (
            client.userInfo(
                OAuthEndpoints.conventional(base).userinfo,
                AccessToken("t", ""),
            ) as ApiResult.Success
            ).value
        assertEquals("alice", info.preferredUsername)
        assertEquals("Bearer t", server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization"))
    }

    @Test
    fun `metadata is read from under the API base, and absence is null`() = runTest {
        respond(
            """{"issuer":"$base","token_endpoint":"${base}oauth/token","code_challenge_methods_supported":["S256"]}""",
        )
        assertEquals(base.toString(), client.metadata(base)?.issuer)
        assertEquals(
            "/index.php/apps/social/.well-known/oauth-authorization-server",
            server.takeRequest(1, TimeUnit.SECONDS)?.url?.encodedPath,
        )
        respond("""{"message":"oauth-authorization-server not supported"}""", code = 404)
        assertNull(client.metadata(base))
    }
}
