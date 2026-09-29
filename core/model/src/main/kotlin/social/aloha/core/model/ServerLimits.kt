// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * The server's own statement of what it accepts. The composer's counter, the media picker's limits and
 * the upload pre-flight all read from here; Mastodon's defaults are the last resort, used only when
 * both instance routes fail.
 *
 * @property imageSizeLimit bytes, for every file. 10 MB by default on Nextcloud Social, which reads an
 *   image whole into memory to strip and resize it.
 * @property videoSizeLimit bytes, for video only. 2048 MB by default on Nextcloud Social, which copies a
 *   video a chunk at a time and never holds it, hence a [Long].
 * @property minPollExpiration seconds.
 * @property maxPollExpiration seconds.
 */
@Serializable
public data class ServerLimits(
    val maxStatusCharacters: Int,
    val maxMediaAttachments: Int,
    val charactersReservedPerUrl: Int,
    val imageSizeLimit: Long,
    val videoSizeLimit: Long,
    val supportedMimeTypes: List<String>,
    val maxPollOptions: Int,
    val maxPollOptionCharacters: Int,
    val minPollExpiration: Long,
    val maxPollExpiration: Long,
    val maxFeaturedTags: Int,
) {
    /** The ceiling for one file: the video limit when the server treats it as video. */
    public fun sizeLimit(mimeType: String): Long = if (mimeType.startsWith("video/")) videoSizeLimit else imageSizeLimit

    /** Whether the server accepts [mimeType]; an empty list means it did not say, so anything goes. */
    public fun accepts(mimeType: String): Boolean = supportedMimeTypes.isEmpty() || mimeType in supportedMimeTypes

    public companion object {
        private const val MEGABYTE = 1024L * 1024L

        public val MastodonDefaults: ServerLimits = ServerLimits(
            maxStatusCharacters = 500,
            maxMediaAttachments = 4,
            charactersReservedPerUrl = 23,
            imageSizeLimit = 10 * MEGABYTE,
            videoSizeLimit = 40 * MEGABYTE,
            supportedMimeTypes = listOf(
                "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif",
                "video/mp4", "video/quicktime", "video/webm", "audio/mpeg", "audio/mp4", "audio/ogg",
            ),
            maxPollOptions = 4,
            maxPollOptionCharacters = 50,
            minPollExpiration = 300,
            maxPollExpiration = 2_629_746,
            maxFeaturedTags = 10,
        )
    }
}
