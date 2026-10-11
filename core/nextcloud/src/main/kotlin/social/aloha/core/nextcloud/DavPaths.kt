// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import okhttp3.HttpUrl

/**
 * WebDAV addresses under a Nextcloud, built a segment at a time so a `#`, `?` or `%` in a name, or a user
 * id that is an e-mail address, is encoded rather than read as part of the address.
 */
internal object DavPaths {
    /** [userId]'s files root below the Nextcloud [root], ending in a slash. */
    fun filesRoot(root: HttpUrl, userId: String): HttpUrl =
        root.newBuilder().addPathSegments("remote.php/dav/files").addPathSegment(userId).addPathSegment("").build()

    /** [path] below [filesRoot]; a folder ends in a slash. Null for a path with a dot segment. */
    fun file(filesRoot: HttpUrl, path: String, folder: Boolean = false): HttpUrl? =
        segments(path)?.let { file(filesRoot, it, folder) }

    /** The file at [segments] below [filesRoot], which [segments] made safe. */
    fun file(filesRoot: HttpUrl, segments: List<String>, folder: Boolean = false): HttpUrl {
        val builder = filesRoot.newBuilder()
        segments.forEach(builder::addPathSegment)
        if (folder && segments.isNotEmpty()) builder.addPathSegment("")
        return builder.build()
    }

    /**
     * [path]'s segments, without the empty ones a leading, trailing or doubled slash makes; null when one
     * is `.` or `..`, which OkHttp would resolve, moving a request that carries the password elsewhere.
     */
    fun segments(path: String): List<String>? =
        path.split('/').filter(String::isNotEmpty).takeIf { segments -> segments.none(::isDotSegment) }

    fun isDotSegment(segment: String): Boolean = segment == "." || segment == ".."
}
