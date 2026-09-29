// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import okhttp3.HttpUrl

/** The tokens and client the mock hands out in place of the corpus's redacted ones. */
public object MockCredentials {
    public const val ACCESS_TOKEN: String = "mock-access-token"
    public const val CLIENT_ID: String = "mock-client-id"
    public const val CLIENT_SECRET: String = "mock-client-secret"

    /** A token the server treats as revoked: every authenticated route answers 401. */
    public const val REVOKED_TOKEN: String = "revoked-token"

    /** What Login Flow v2 hands out on the connected configuration. */
    public const val APP_PASSWORD: String = "mock-app-password"
}

/** How one configuration routes, refuses and rewrites. */
internal class Routes(private val configuration: MockServerConfiguration) {
    private val nextcloud = configuration != MockServerConfiguration.Mastodon

    /** The rate-limited shape refuses the first read of every path, once, with `Retry-After: 1`. */
    fun refusal(method: String, path: String, seen: MutableSet<String>): MockResponse? {
        val throttled = configuration == MockServerConfiguration.RateLimited && method == "GET" && seen.add(path)
        return if (throttled) {
            MockResponse.Builder().code(
                TOO_MANY,
            ).addHeader("Retry-After", "1").body("""{"error":"Too many requests"}""").build()
        } else {
            null
        }
    }

    /** The path the corpus knows this request by, or null when the configuration has no such route. */
    fun resolvePath(path: String): String? {
        if (configuration == MockServerConfiguration.CoreOnly &&
            EXTENSION_PREFIXES.any { path.contains(it) }
        ) {
            return null
        }
        val aliased =
            nextcloud && configuration.servesApiAtRoot && (path.startsWith("/api/") || path.startsWith("/oauth/"))
        return if (aliased) APP_PREFIX + path else path
    }

    fun unauthorised(path: String, authorization: String?): Fixture? {
        if (!nextcloud || PROTECTED.none { path.contains(it) }) return null
        return when (authorization) {
            null -> FixtureCorpus.nextcloudSocial.named("errors/401-no-token.json")

            "Bearer ${MockCredentials.REVOKED_TOKEN}" -> FixtureCorpus.nextcloudSocial.named(
                "errors/401-bad-token.json",
            )

            else -> null
        }
    }

    /** Answers the corpus cannot hold: the sign-in routes of the Mastodon shape and revocation. */
    fun synthesized(method: String, url: HttpUrl, authorization: String?): Fixture? {
        val path = resolvePath(url.encodedPath) ?: return null
        return when {
            method == "POST" && path.endsWith("/oauth/revoke") -> json(path, "{}")
            configuration == MockServerConfiguration.NextcloudConnected -> loginFlow(method, path)
            configuration == MockServerConfiguration.Mastodon -> mastodon(method, path, authorization)
            else -> null
        }
    }

    /** The sign-in routes of the Mastodon shape, which the captured discovery documents do not include. */
    private fun mastodon(method: String, path: String, authorization: String?): Fixture? = when {
        method == "POST" && path == "/api/v1/apps" -> json(path, REGISTRATION)

        method == "POST" && path == "/oauth/token" -> json(path, TOKEN)

        path == "/api/v1/accounts/verify_credentials" && authorization == null ->
            json(path, """{"error":"The access token is invalid"}""", UNAUTHORISED)

        path == "/api/v1/accounts/verify_credentials" -> json(path, ACCOUNT)

        path.startsWith("/api/v1/timelines/") -> json(path, "[]")

        else -> null
    }

    /** Login Flow v2 as Nextcloud answers it: start, then poll until the person approved in the browser. */
    private fun loginFlow(method: String, path: String): Fixture? = when {
        method == "POST" && path == "/index.php/login/v2" -> json(path, LOGIN_FLOW_START)
        method == "POST" && path == "/index.php/login/v2/poll" -> json(path, LOGIN_FLOW_RESULT)
        else -> null
    }

    /** The corpus answer as this configuration gives it. */
    fun adjust(fixture: Fixture, path: String): Fixture {
        // corpus bodies are re-serialised compactly, so one spelling of each redaction covers them
        val body = fixture.body
            .replace("\"access_token\":\"REDACTED\"", "\"access_token\":\"${MockCredentials.ACCESS_TOKEN}\"")
            .replace("\"client_id\":\"REDACTED\"", "\"client_id\":\"${MockCredentials.CLIENT_ID}\"")
            .replace("\"client_secret\":\"REDACTED\"", "\"client_secret\":\"${MockCredentials.CLIENT_SECRET}\"")
        val malformed = configuration == MockServerConfiguration.MalformedEntities && path.contains("/timelines/")
        return fixture.copy(body = if (malformed) breakThirdEntry(body) else body)
    }

    /** Replaces the third element with an entity that has no id and an account that is not an object. */
    private fun breakThirdEntry(body: String): String {
        val array = Json.parseToJsonElement(body) as? JsonArray ?: return body
        if (array.size < MALFORMED_INDEX + 1) return body
        val broken = JsonObject(
            mapOf(
                "id" to kotlinx.serialization.json.JsonNull,
                "account" to JsonPrimitive(MALFORMED_INDEX),
            ),
        )
        return JsonArray(array.toMutableList().apply { set(MALFORMED_INDEX, broken) }).toString()
    }

    private fun json(path: String, body: String, status: Int = OK) = Fixture(
        name = "synthesized$path",
        method = "",
        path = path,
        query = emptyMap(),
        status = status,
        headers = mapOf("content-type" to "application/json; charset=utf-8"),
        body = body,
    )

    private companion object {
        const val OK = 200
        const val UNAUTHORISED = 401
        const val TOO_MANY = 429
        const val MALFORMED_INDEX = 2
        const val APP_PREFIX = "/index.php/apps/social"

        /** Routes that exist only on Nextcloud Social (or Pixelfed/PeerTube shapes it serves). */
        val EXTENSION_PREFIXES = listOf(
            "/stories", "/collections", "/interests", "/videos/continue", "/watched", "/places", "/starter_packs",
            "/highlights", "/reactions", "/discover/", "/api/v1.1/", "/api/v1.2/", "/api/v2/config", "/api/v1/config",
            "/api/v2/notifications", "/timelines/interests",
        )

        /** Routes that need a viewer; the rest of the corpus answers anonymously. */
        val PROTECTED = listOf(
            "/accounts/verify_credentials", "/timelines/home", "/timelines/list", "/notifications", "/bookmarks",
            "/favourites", "/markers", "/conversations", "/lists", "/follow_requests", "/preferences", "/filters",
            "/blocks", "/mutes", "/scheduled_statuses", "/oauth/userinfo",
        )

        const val LOGIN_FLOW_START =
            """{"poll":{"token":"mock-poll-token","endpoint":"http://nextcloud.local/index.php/login/v2/poll"},""" +
                """"login":"http://nextcloud.local/index.php/login/v2/flow/mock"}"""

        const val LOGIN_FLOW_RESULT =
            """{"server":"http://nextcloud.local","loginName":"alice",""" +
                """"appPassword":"${MockCredentials.APP_PASSWORD}"}"""

        const val REGISTRATION =
            """{"id":"1","name":"Aloha Social","website":"https://aloha.social",""" +
                """"scopes":["read","write","follow","push"],""" +
                """"redirect_uris":["https://aloha.social/oauth/callback","alohasocial://oauth-callback"],""" +
                """"client_id":"${MockCredentials.CLIENT_ID}","client_secret":"${MockCredentials.CLIENT_SECRET}"}"""

        const val TOKEN =
            """{"access_token":"${MockCredentials.ACCESS_TOKEN}","token_type":"Bearer",""" +
                """"scope":"read write follow push","created_at":1790000000}"""

        const val ACCOUNT =
            """{"id":"109000000000000001","username":"tester","acct":"tester","display_name":"Test Person",""" +
                """"locked":false,"bot":false,"created_at":"2026-01-01T00:00:00.000Z","note":"",""" +
                """"url":"https://mastodon.social/@tester","avatar":"","avatar_static":"",""" +
                """"header":"","header_static":"","followers_count":0,"following_count":0,"statuses_count":0,""" +
                """"fields":[],"emojis":[],"source":{"privacy":"public","sensitive":false,"language":"en",""" +
                """"note":"","fields":[],"follow_requests_count":0}}"""
    }
}
