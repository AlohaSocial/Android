// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import java.io.File
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source

/** At most this much goes in one request; a larger file goes in chunks of it, Nextcloud's minimum being 5 MiB. */
internal const val CHUNK_BYTES: Long = 10L * 1024 * 1024

/** How many names an upload tries, `photo.jpg`, `photo (2).jpg` and on, before it gives up. */
internal const val MAX_NAMES: Int = 100

/** [name] first, then `name (2).ext`, `name (3).ext` and on, the way Nextcloud names a copy. */
internal fun freeNames(name: String): Sequence<String> {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val base = name.substring(0, dot)
    val extension = name.substring(dot)
    return sequenceOf(name) + (2..MAX_NAMES).asSequence().map { "$base ($it)$extension" }
}

/** A name a file can be saved under: one segment, not a dot segment. */
internal fun isFileName(name: String): Boolean = name.isNotBlank() && '/' !in name && !DavPaths.isDotSegment(name)

/** The chunk numbers of a file of [size] bytes and the byte range each covers, from 1. */
internal fun chunks(size: Long): List<Pair<Int, LongRange>> =
    (0 until (size + CHUNK_BYTES - 1) / CHUNK_BYTES).map { index ->
        val start = index * CHUNK_BYTES
        (index + 1).toInt() to (start until minOf(start + CHUNK_BYTES, size))
    }

internal fun LongRange.length(): Long = last - first + 1

/** [range] of [file], streamed from disk each time it is written, so a retried request sends it again. */
internal class FileRange(private val file: File, private val range: LongRange) : RequestBody() {
    override fun contentType(): MediaType = OCTET_STREAM

    override fun contentLength(): Long = range.length()

    override fun writeTo(sink: BufferedSink) {
        file.source().buffer().use { source ->
            source.skip(range.first)
            sink.write(source, contentLength())
        }
    }
}

internal val OCTET_STREAM: MediaType = "application/octet-stream".toMediaType()
