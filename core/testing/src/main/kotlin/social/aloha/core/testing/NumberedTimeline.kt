// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl

/**
 * A timeline of numbered statuses, paged the way Mastodon and Nextcloud Social page it (checked against
 * the dev instance): `max_id` answers what lies below, `min_id` the posts immediately above, `since_id`
 * the newest above. New posts are added by raising [newest]. Favouriting, boosting and bookmarking answer
 * as the server would, or with a server error while [failActions] is set; filters and markers answer empty.
 * [media] gives a status its attachments; with [narrows] set the server honours `only_media`,
 * `only_video` and `only_short` as Nextcloud Social does, leaving out what does not match before it pages.
 * What is [hidden] is left out after paging, as Nextcloud Social's home leaves out what the reader's Bluesky
 * preferences hide: a page can come back short, or empty, and still name the next one.
 */
public class NumberedTimeline(template: JsonObject = homeTemplate()) : Dispatcher() {
    @Volatile public var newest: Int = 0

    @Volatile public var failActions: Boolean = false

    @Volatile public var media: (Int) -> List<JsonObject> = { emptyList() }

    @Volatile public var narrows: Boolean = false

    @Volatile public var hidden: (Int) -> Boolean = { false }

    /** Fields a status has beyond the template's, such as its own `uri` and `created_at`. */
    @Volatile public var fields: (Int) -> Map<String, JsonPrimitive> = { emptyMap() }

    /** The home read marker the server keeps, by post number; null for a server that keeps none. */
    @Volatile public var homeMarker: Int? = null
    private val template = JsonObject(template + ("reblog" to JsonNull))

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val action = ACTION.matchEntire(path)?.takeIf { request.method == "POST" }
        return when {
            action != null -> action(action.groupValues[1], action.groupValues[2])
            path.endsWith("/filters") -> json("[]")
            path.endsWith("/markers") -> json(homeMarker?.let { marker(it) } ?: "{}")
            else -> page(request.url)
        }
    }

    private fun marker(post: Int) = """{"home":{"last_read_id":"$post","version":1}}"""

    private fun page(url: HttpUrl): MockResponse {
        val ids = pick(url, url.queryParameter("limit")?.toInt() ?: DEFAULT_LIMIT)
        val body = JsonArray(ids.filterNot(hidden).map { status(it) })
        val builder = MockResponse.Builder().code(
            OK,
        ).body(body.toString()).addHeader("content-type", "application/json")
        ids.lastOrNull()?.let {
            val next = url.newBuilder()
                .removeAllQueryParameters("min_id")
                .removeAllQueryParameters("since_id")
                .setQueryParameter("max_id", it.toString())
                .build()
            val prev = url.newBuilder()
                .removeAllQueryParameters("max_id")
                .removeAllQueryParameters("since_id")
                .setQueryParameter("min_id", ids.max().toString())
                .build()
            builder.addHeader("link", "<$next>; rel=\"next\", <$prev>; rel=\"prev\"")
        }
        return builder.build()
    }

    private fun status(id: String, changes: Map<String, JsonPrimitive> = emptyMap()) =
        JsonObject(template + ("id" to JsonPrimitive(id)) + changes)

    private fun status(id: Int) =
        JsonObject(status(id.toString(), fields(id)) + ("media_attachments" to JsonArray(media(id))))

    /** Whether the server lets [id] through the narrowing [url] asks for. */
    private fun passes(url: HttpUrl, id: Int): Boolean {
        if (!narrows) return true
        val types = media(id).map { it["type"].toString().trim('"') }
        return when {
            url.queryParameter("only_short") == "true" -> media(id).singleOrNull()?.let(::isShort) == true
            url.queryParameter("only_video") == "true" -> "video" in types
            url.queryParameter("only_media") == "true" -> types.isNotEmpty()
            else -> true
        }
    }

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
            max != null -> (max - 1 downTo 1).filter { passes(url, it) }.take(limit)
            min != null -> (min + 1..newest).filter { passes(url, it) }.take(limit).reversed()
            else -> (newest downTo (since ?: 0) + 1).filter { passes(url, it) }.take(limit)
        }
    }

    public companion object {
        /** A photo attachment of status [id]. */
        public fun image(id: Int): JsonObject =
            attachment(id, "image", mapOf("width" to JsonPrimitive(800), "height" to JsonPrimitive(600)))

        /** A video attachment of status [id], [duration] seconds long; null leaves the length out. */
        public fun video(id: Int, width: Int, height: Int, duration: Double?): JsonObject {
            val original = buildMap {
                put("width", JsonPrimitive(width))
                put("height", JsonPrimitive(height))
                duration?.let { put("duration", JsonPrimitive(it)) }
            }
            return attachment(id, "video", original)
        }

        /** A video attachment of status [id] the server did not describe, as one without ffmpeg sends it. */
        public fun undescribed(id: Int): JsonObject = attachment(id, "video", emptyMap())

        private fun attachment(id: Int, type: String, original: Map<String, JsonPrimitive>): JsonObject = JsonObject(
            mapOf(
                "id" to JsonPrimitive("m$id"),
                "type" to JsonPrimitive(type),
                "url" to JsonPrimitive("https://media.example/$id"),
                "preview_url" to JsonPrimitive("https://media.example/$id/preview"),
                "meta" to JsonObject(mapOf("original" to JsonObject(original))),
            ),
        )

        /** Nextcloud Social's rule for `only_short`: a video no longer than 180 seconds, no wider than square. */
        private fun isShort(attachment: JsonObject): Boolean {
            val original = attachment["meta"]?.jsonObject?.get("original")?.jsonObject ?: return false
            fun number(key: String) = original[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val duration = number("duration")
            val width = number("width")
            val height = number("height")
            return attachment["type"]?.jsonPrimitive?.content == "video" &&
                duration > 0 && duration <= SHORT_SECONDS && height > 0 && width > 0 && width <= height
        }

        private const val SHORT_SECONDS = 180.0
        private val ACTION = Regex(".*/api/v1/statuses/([^/]+)/(\\w+)")
        private const val DEFAULT_LIMIT = 20
        private const val OK = 200
        private const val SERVER_ERROR = 500

        /** The first status of the recorded Nextcloud Social home timeline, as every numbered status's shape. */
        public fun homeTemplate(): JsonObject {
            val fixture = checkNotNull(
                NumberedTimeline::class.java.getResource("/fixtures/nextcloud-social-0.26.97/api/timeline-home.json"),
            ).readText()
            return Json.parseToJsonElement(fixture).jsonObject.getValue("body").jsonArray.first().jsonObject
        }
    }
}
