// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl

/** The server shapes the app has to cope with; sign-in has to succeed against all but the refusing ones. */
public enum class MockServerConfiguration {
    /** Nextcloud Social with the administrator's root rewrite rules: the API answers at `/` too. */
    NextcloudWithRewrite,

    /** Nextcloud Social without them: the API answers only under `/index.php/apps/social/`. */
    NextcloudWithoutRewrite,

    /** Nextcloud Social whose optional Nextcloud connection is available (Login Flow v2, OCS capabilities). */
    NextcloudConnected,

    /** Stock Mastodon: discovery documents captured from mastodon.social, no Nextcloud extension. */
    Mastodon,

    /** The Mastodon core only; every extension route answers 404. */
    CoreOnly,

    /**
     * Answers the first read of every path with 429 and `Retry-After: 1`, then as Nextcloud Social
     * with the rewrite rules.
     */
    RateLimited,

    /** Nextcloud Social whose timelines carry one malformed entity in third place. */
    MalformedEntities,
}

/**
 * A MockWebServer that answers from the fixture corpus in the shape of one [configuration]. Absolute
 * URLs in bodies and headers are rewritten to this server's origin, so cursors and endpoints the
 * server names can be followed. Every request is recorded in [requests].
 */
public class MockSocialServer(public val configuration: MockServerConfiguration) : AutoCloseable {
    private val server = MockWebServer()
    private val pins = mutableMapOf<String, Fixture>()
    private val throttled: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val recorded = CopyOnWriteArrayList<RecordedRequest>()

    /** Every request this server answered, in order. */
    public val requests: List<RecordedRequest> get() = recorded

    /** Where the Mastodon API lives in this configuration. */
    public val apiBase: HttpUrl
        get() = server.url(if (configuration.servesApiAtRoot) "/" else "/index.php/apps/social/")

    /** The origin a person would type. */
    public val origin: HttpUrl get() = server.url("/")

    /** Starts on [port], or on any free one when 0. */
    public fun start(port: Int = 0): MockSocialServer = apply {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                return answer(request)
            }
        }
        server.start(port)
    }

    /** Answers `method path` (path without query) with the fixture [name] from now on, e.g. a per-person variant. */
    public fun pin(method: String, path: String, name: String) {
        pins["$method ${appPath(path)}"] = corpusFor(configuration).named(name)
    }

    /** Answers the request a fixture was captured for with that fixture, whatever else would match. */
    public fun pinFixture(name: String) {
        val fixture = corpusFor(configuration).named(name)
        pins["${fixture.method} ${fixture.path}"] = fixture
    }

    /** Answers `method path` with [status] and [body] from now on, for answers the corpus does not hold. */
    public fun pin(method: String, path: String, status: Int, body: String) {
        pins["$method ${appPath(path)}"] = Fixture(
            name = "pinned$path",
            method = method,
            path = appPath(path),
            query = emptyMap(),
            status = status,
            headers = mapOf("content-type" to "application/json; charset=utf-8"),
            body = body,
        )
    }

    override fun close() {
        server.close()
    }

    private fun answer(request: RecordedRequest): MockResponse {
        val routes = Routes(configuration)
        val authorization = request.headers["Authorization"]
        val path = routes.resolvePath(request.url.encodedPath)
        val fixture = routes.synthesized(request.method, request.url, authorization)
            ?: path?.let { routes.unauthorised(it, authorization) }
            ?: path?.let {
                pins["${request.method} $it"] ?: bestMatch(request.method, it, request.url)
            }?.let { routes.adjust(it, path) }
        return routes.refusal(request.method, request.url.encodedPath, throttled) ?: fixture?.let(::rewrite)
            ?: notFound()
    }

    /**
     * The fixture for this request: same method and path, and every query parameter the fixture was
     * captured with present in the request, except `limit`, which only breaks ties. A probe asking for
     * `limit=1` gets the page captured with `limit=20`.
     */
    private fun bestMatch(method: String, path: String, url: HttpUrl): Fixture? {
        val query = url.queryParameterNames.associateWith { url.queryParameterValues(it).filterNotNull() }
        return corpusFor(configuration).fixtures
            .filter {
                it.method == method && it.path.trimEnd('/') == path.trimEnd('/') &&
                    configuration.includes(it.name)
            }
            .filter { fixture -> fixture.query.all { (key, values) -> key == LIMIT || query[key] == values } }
            .sortedWith(
                compareByDescending<Fixture> { it.query.keys.count { key -> key != LIMIT } }
                    .thenByDescending { it.query[LIMIT] == query[LIMIT] }
                    .thenBy { it.name.length },
            )
            .firstOrNull()
    }

    private fun rewrite(fixture: Fixture): MockResponse {
        val origin = server.url("/").toString().trimEnd('/')
        fun swap(text: String) = CAPTURED_ORIGINS.fold(text) { acc, captured -> acc.replace(captured, origin) }
        val builder = MockResponse.Builder().code(fixture.status).body(swap(fixture.body))
        fixture.headers.filterKeys {
            it.lowercase() != "content-length"
        }.forEach { (name, value) -> builder.addHeader(name, swap(value)) }
        return builder.build()
    }

    private fun notFound() = MockResponse.Builder().code(NOT_FOUND).body("""{"error":"Not found"}""").build()

    private companion object {
        const val NOT_FOUND = 404
        const val LIMIT = "limit"
        val CAPTURED_ORIGINS = listOf("http://nextcloud.local", "https://mastodon.social")

        fun corpusFor(configuration: MockServerConfiguration) = if (configuration ==
            MockServerConfiguration.Mastodon
        ) {
            FixtureCorpus.mastodonSocial
        } else {
            FixtureCorpus.nextcloudSocial
        }

        fun appPath(path: String) =
            if (path.startsWith("/index.php/apps/social")) path else "/index.php/apps/social$path"
    }
}

internal val MockServerConfiguration.servesApiAtRoot: Boolean
    get() = this != MockServerConfiguration.NextcloudWithoutRewrite &&
        this != MockServerConfiguration.CoreOnly &&
        this != MockServerConfiguration.MalformedEntities

/**
 * Which corpus files a configuration answers from. `plain/` holds what a server without the rewrite
 * rules answers and `root/` what one with them answers; a file only one of them has (the OCS
 * capabilities, Pixelfed's config) does not depend on the rules and is shared. `errors/` answers
 * only when a test pins it or a routing rule asks for it: it shares its paths with the successes.
 */
internal fun MockServerConfiguration.includes(name: String): Boolean {
    val folder = name.substringBefore('/')
    val file = name.substringAfter('/')
    val corpus = FixtureCorpus.nextcloudSocial
    return when {
        this == MockServerConfiguration.Mastodon -> true
        folder == "errors" -> false
        servesApiAtRoot -> folder != "plain" || corpus.fixtures.none { it.name == "root/$file" }
        else -> folder != "root" || corpus.fixtures.none { it.name == "plain/$file" }
    }
}
