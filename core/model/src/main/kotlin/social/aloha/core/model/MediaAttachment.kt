// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * A file attached to a status.
 *
 * @property previewUrl for a video, its poster frame served as an image.
 * @property description the alt text.
 * @property hlsUrl the master playlist of a local video's transcoding ladder on Nextcloud Social, where
 *   the administrator enabled it. Never a replacement for [url]: the whole file is always there too.
 */
@Serializable
public data class MediaAttachment(
    val id: String,
    val type: AttachmentKind,
    val url: String? = null,
    val previewUrl: String? = null,
    val remoteUrl: String? = null,
    val description: String? = null,
    val blurhash: String? = null,
    val meta: MediaMeta? = null,
    val hlsUrl: String? = null,
) {
    val hasAltText: Boolean get() = !description.isNullOrBlank()

    /** Width over height, where the server said; null defers Shorts classification. */
    val aspectRatio: Double? get() = meta?.original?.resolvedAspect

    /** Seconds, where the server said. */
    val duration: Double? get() = meta?.original?.duration

    /** A box to lay out in before the bytes arrive, so nothing shifts. */
    val displayAspectRatio: Double get() = aspectRatio ?: DEFAULT_ASPECT

    public companion object {
        public const val DEFAULT_ASPECT: Double = 4.0 / 3.0
    }
}

@Serializable
public data class MediaMeta(
    val original: MediaDimensions? = null,
    val small: MediaDimensions? = null,
    val focus: MediaFocus? = null,
)

@Serializable
public data class MediaDimensions(
    val width: Int? = null,
    val height: Int? = null,
    val size: String? = null,
    val aspect: Double? = null,
    val duration: Double? = null,
    val frameRate: String? = null,
    val bitrate: Int? = null,
) {
    /** The server's own `aspect` where positive, else width over height; null when neither is known. */
    val resolvedAspect: Double?
        get() {
            if (aspect != null && aspect > 0) return aspect
            if (width == null || height == null || height <= 0) return null
            return width.toDouble() / height
        }
}

/** A focal point, each axis from -1 to 1 with y up. */
@Serializable
public data class MediaFocus(val x: Double, val y: Double)
