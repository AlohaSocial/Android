// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import android.util.Xml
import java.io.InputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import okhttp3.HttpUrl
import org.xmlpull.v1.XmlPullParser

/**
 * Reads a WebDAV `207 Multi-Status` answer to a listing. Android's pull parser does not process a
 * `DOCTYPE` unless asked to, so an answer cannot pull in external entities.
 *
 * A property the server does not have comes back in a `propstat` of its own with a 404 status and no
 * value; only values are read, so those fall away by themselves.
 */
internal object Multistatus {
    private const val DAV = "DAV:"
    private const val OWNCLOUD = "http://owncloud.org/ns"
    private const val NEXTCLOUD = "http://nextcloud.org/ns"

    /**
     * A listing of [folder]: the folder itself and what is in it, each with its path below [filesRoot],
     * the person's files root. Entries outside the root and entries with a dot segment are left out.
     */
    fun parse(stream: InputStream, filesRoot: HttpUrl, folder: String): NextcloudFolder {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(stream, null)
        val listing = Listing(filesRoot, path = DavPaths.segments(folder).orEmpty().joinToString("/"))
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> listing.start(parser.namespace, parser.name)
                XmlPullParser.TEXT -> listing.text(parser.text)
                XmlPullParser.END_TAG -> listing.end(parser.namespace, parser.name)
            }
        }
        return NextcloudFolder(listing.self, listing.files)
    }

    /** The entries read so far, the one being read, and the text of the element being read. */
    private class Listing(private val filesRoot: HttpUrl, private val path: String) {
        var self: NextcloudFile? = null
        val files = mutableListOf<NextcloudFile>()
        private var entry: Entry? = null
        private val text = StringBuilder()

        fun start(namespace: String, name: String) {
            text.setLength(0)
            when (namespace to name) {
                DAV to "response" -> entry = Entry()
                DAV to "collection" -> entry?.folder = true
            }
        }

        fun text(value: String) {
            text.append(value)
        }

        fun end(namespace: String, name: String) {
            val current = entry
            when {
                current == null -> Unit

                namespace == DAV && name == "response" -> {
                    val file = current.toFile(filesRoot)
                    when {
                        file?.path == path -> self = file
                        file != null && file.path.isNotEmpty() -> files += file
                    }
                    entry = null
                }

                else -> current.read(namespace, name, text.toString().trim())
            }
            text.setLength(0)
        }
    }

    private class Entry {
        var href = ""
        var folder = false
        var mimeType: String? = null
        var modified: Instant? = null
        var etag = ""
        var fileId: Long? = null
        var size = 0L
        var permissions = ""
        var hasPreview = false
        var encrypted = false

        fun read(namespace: String, name: String, value: String) {
            if (value.isEmpty()) return
            when (namespace to name) {
                DAV to "href" -> href = value

                DAV to "getcontenttype" -> mimeType = value

                DAV to "getlastmodified" -> modified = httpDate(value)

                DAV to "getetag" -> etag = value.removeSurrounding("\"")

                OWNCLOUD to "fileid" -> fileId = value.toLongOrNull()

                OWNCLOUD to "size" -> size = value.toLongOrNull() ?: 0L

                // an upload's chunks have only this; a file has both, and oc:size covers folders too
                DAV to "getcontentlength" -> if (size == 0L) size = value.toLongOrNull() ?: 0L

                OWNCLOUD to "permissions" -> permissions = value

                NEXTCLOUD to "has-preview" -> hasPreview = value == "true"

                NEXTCLOUD to "is-encrypted" -> encrypted = value == "1" || value == "true"
            }
        }

        fun toFile(filesRoot: HttpUrl): NextcloudFile? {
            val below = filesRoot.resolve(href)?.let { below(it, filesRoot) } ?: return null
            return NextcloudFile(
                path = below.joinToString("/"),
                isFolder = folder,
                mimeType = mimeType,
                size = size,
                modified = modified,
                etag = etag,
                fileId = fileId,
                permissions = permissions,
                hasPreview = hasPreview,
                encrypted = encrypted,
            )
        }
    }

    /** [url]'s segments below [filesRoot], on its origin and without a dot segment; null for any other. */
    private fun below(url: HttpUrl, filesRoot: HttpUrl): List<String>? {
        val sameOrigin = url.scheme == filesRoot.scheme && url.host == filesRoot.host && url.port == filesRoot.port
        val segments = url.pathSegments.filter(String::isNotEmpty)
        val root = filesRoot.pathSegments.filter(String::isNotEmpty)
        return segments.drop(root.size).takeIf { below ->
            sameOrigin && segments.take(root.size) == root && below.none(DavPaths::isDotSegment)
        }
    }

    private fun httpDate(value: String): Instant? = try {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}
