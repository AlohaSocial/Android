// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import java.io.File

public enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

/** Which credential a route takes. */
public enum class Authentication {
    /** A public route: no credential is sent even when the account has one. */
    None,

    /** The Social OAuth bearer token. */
    Bearer,

    /**
     * The Nextcloud app password as HTTP Basic, sent with `OCS-APIRequest: true`, without which
     * Nextcloud Social answers 401 and non-OCS writes answer 412 "CSRF check failed".
     */
    NextcloudSession,

    /** A public Nextcloud OCS route: no credential, but `OCS-APIRequest: true`, which OCS requires of every call. */
    NextcloudPublic,
}

public data class QueryItem(val name: String, val value: String)

/** A request body. */
public sealed interface Body {
    public data object None : Body

    public data class Form(val fields: List<QueryItem>) : Body

    public data class Json(val text: String) : Body

    public data class Multipart(val parts: List<Part>) : Body
}

/** One part of a multipart body; files are streamed from disk, never held in memory. */
public sealed interface Part {
    public val name: String

    public data class Field(override val name: String, val value: String) : Part

    /** A file streamed from disk; [onProgress] hears how many of its bytes have gone, and of how many. */
    public data class FileContent(
        override val name: String,
        val file: File,
        val fileName: String,
        val mimeType: String,
        val onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ) : Part
}

/**
 * A request described relative to an account's API base. Endpoints are built only by the
 * `*Endpoints` factory objects in `social.aloha.core.network.endpoints`, never by concatenating paths
 * at a call site.
 *
 * @property path relative to the API base, without a leading slash.
 * @property idempotencyKey sent as `Idempotency-Key`, which Nextcloud Social honours: two posts with
 *   the same key return the same status, so a write that timed out can be retried safely.
 */
public data class Endpoint(
    val path: String,
    val method: HttpMethod = HttpMethod.GET,
    val query: List<QueryItem> = emptyList(),
    val body: Body = Body.None,
    val authentication: Authentication = Authentication.Bearer,
    val idempotencyKey: String? = null,
) {
    init {
        require(!path.startsWith("/")) { "Endpoint paths are relative to the API base: $path" }
    }

    public fun adding(items: List<QueryItem>): Endpoint = copy(query = query + items)
}

/** `name=value`, or nothing when [value] is null. */
public fun queryOf(name: String, value: String?): List<QueryItem> = value?.let { listOf(QueryItem(name, it)) }.orEmpty()

/** Mastodon's array parameters: `types[]=mention&types[]=follow`. */
public fun repeatedQuery(name: String, values: Collection<String>): List<QueryItem> =
    values.map { QueryItem("$name[]", it) }

/**
 * A flag is sent only when true: an explicit `only_video=false` is noise, and some forks read any
 * presence as true.
 */
public fun flagQuery(name: String, value: Boolean): List<QueryItem> =
    if (value) listOf(QueryItem(name, "true")) else emptyList()
