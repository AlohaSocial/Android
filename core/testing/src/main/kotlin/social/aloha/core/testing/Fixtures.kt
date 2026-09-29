// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One recorded exchange: the request that was sent and the answer a real server gave. Captured from
 * a live instance, secrets redacted, so tests are built on what servers actually do rather than on
 * what their documentation says.
 */
public data class Fixture(
    val name: String,
    val method: String,
    val path: String,
    val query: Map<String, List<String>>,
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
)

/** A corpus of fixtures under `fixtures/<folder>/` on the classpath, listed by its `index.txt`. */
public class FixtureCorpus(public val folder: String) {
    public val fixtures: List<Fixture> by lazy {
        resource("index.txt").lineSequence().map(String::trim).filter(String::isNotEmpty).map(::load).toList()
    }

    /** The fixture stored as [name], e.g. `api/timeline-home.json`. */
    public fun named(name: String): Fixture =
        fixtures.firstOrNull { it.name == name } ?: error("no fixture $name in $folder")

    private fun load(name: String): Fixture {
        val root = Json.parseToJsonElement(resource(name)).jsonObject
        val request = root.getValue("request").jsonObject
        val target = request.getValue("path").jsonPrimitive.content
        val body = root["body"]
        return Fixture(
            name = name,
            method = request.getValue("method").jsonPrimitive.content,
            path = target.substringBefore('?'),
            query = parseQuery(target.substringAfter('?', missingDelimiterValue = "")),
            status = root.getValue("status").jsonPrimitive.int,
            headers = (root["headers"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content },
            body = when (body) {
                null -> ""
                is JsonPrimitive -> body.content
                else -> body.toString()
            },
        )
    }

    private fun resource(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$folder/$name")
            ?: error("fixture resource fixtures/$folder/$name is not on the classpath")
        return stream.use { it.readBytes().decodeToString() }
    }

    public companion object {
        public val nextcloudSocial: FixtureCorpus by lazy { FixtureCorpus("nextcloud-social-0.26.97") }
        public val mastodonSocial: FixtureCorpus by lazy { FixtureCorpus("mastodon-social-4.8.0-alpha.3") }

        internal fun parseQuery(query: String): Map<String, List<String>> = query.split('&')
            .filter { it.isNotEmpty() }
            .map { it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "") }
            .groupBy({ decode(it.first) }, { decode(it.second) })

        private fun decode(text: String): String = java.net.URLDecoder.decode(text, "UTF-8")
    }
}
