// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl

/**
 * A timeline of numbered statuses, paged the way Mastodon and Nextcloud Social page it (checked against
 * the dev instance): `max_id` answers what lies below, `min_id` the posts immediately above, `since_id`
 * the newest above. New posts are added by raising [newest]. Favouriting, boosting and bookmarking answer
 * as the server would, or with a server error while [failActions] is set; filters and markers answer empty.
 */
public class NumberedTimeline(template: JsonObject = homeTemplate()) : Dispatcher() {
    @Volatile public var newest: Int = 0

    @Volatile public var failActions: Boolean = false
    private val template = JsonObject(template + ("reblog" to JsonNull))

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val action = ACTION.matchEntire(path)?.takeIf { request.method == "POST" }
        return when {
            action != null -> action(action.groupValues[1], action.groupValues[2])
            path.endsWith("/filters") -> json("[]")
            path.endsWith("/markers") -> json("{}")
            else -> page(request.url)
        }
    }

    private fun page(url: HttpUrl): MockResponse {
        val ids = pick(url, url.queryParameter("limit")?.toInt() ?: DEFAULT_LIMIT)
        val body = JsonArray(ids.map { status(it.toString()) })
        val builder = MockResponse.Builder().code(
            OK,
        ).body(body.toString()).addHeader("content-type", "application/json")
        ids.lastOrNull()?.let {
            val next = url.newBuilder()
                .removeAllQueryParameters("min_id")
                .removeAllQueryParameters("since_id")
                .setQueryParameter("max_id", it.toString())
                .build()
            builder.addHeader("link", "<$next>; rel=\"next\"")
        }
        return builder.build()
    }

    private fun status(id: String, changes: Map<String, JsonPrimitive> = emptyMap()) =
        JsonObject(template + ("id" to JsonPrimitive(id)) + changes)

    private fun action(id: String, action: String): MockResponse {
        if (failActions) return MockResponse.Builder().code(SERVER_ERROR).body("{}").build()
        val on = !action.startsWith("un")
        val field = when (action.removePrefix("un")) {
            "favourite" -> "favourited"
            "reblog" -> "reblogged"
            "bookmark" -> "bookmarked"
            "pin" -> "pinned"
            else -> "muted"
        }
        val updated = status(id, mapOf(field to JsonPrimitive(on)))
        // boosting answers with the new boost, which carries the post
        val body = if (action == "reblog") JsonObject(status("boost-$id") + ("reblog" to updated)) else updated
        return json(body.toString())
    }

    private fun json(body: String) =
        MockResponse.Builder().code(OK).body(body).addHeader("content-type", "application/json").build()

    private fun pick(url: HttpUrl, limit: Int): List<Int> {
        val max = url.queryParameter("max_id")?.toInt()
        val min = url.queryParameter("min_id")?.toInt()
        val since = url.queryParameter("since_id")?.toInt()
        return when {
            max != null -> (max - 1 downTo 1).take(limit)
            min != null -> (min + 1..newest).take(limit).reversed()
            else -> (newest downTo (since ?: 0) + 1).take(limit)
        }
    }

    public companion object {
        private val ACTION = Regex(".*/api/v1/statuses/([^/]+)/(\\w+)")
        private const val DEFAULT_LIMIT = 20
        private const val OK = 200
        private const val SERVER_ERROR = 500

        /** The first status of the recorded Nextcloud Social home timeline, as every numbered status's shape. */
        public fun homeTemplate(): JsonObject {
            val fixture = NumberedTimeline::class.java.classLoader!!
                .getResource("fixtures/nextcloud-social-0.26.97/api/timeline-home.json")!!
                .readText()
            return Json.parseToJsonElement(fixture).jsonObject.getValue("body").jsonArray.first().jsonObject
        }
    }
}
