// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * Decides what a status is, so the media modes can route it. No server supports Shorts natively, so
 * these rules are the contract; everything else asks this.
 */
public object ContentClassifier {
    /**
     * A short is at most this long, in seconds. Generous on purpose: it catches the vertical video that
     * is a short in everything but duration.
     */
    public const val MAXIMUM_SHORT_DURATION: Double = 180.0

    /** Under this, duration alone makes a short regardless of shape. */
    public const val UNCONDITIONAL_SHORT_DURATION: Double = 60.0

    /** Portrait or square; anything wider is a video, not a short. */
    public const val MAXIMUM_SHORT_ASPECT: Double = 1.0

    public val shortHashtags: Set<String> = setOf("loops", "short", "shorts", "reel", "reels")

    public enum class Shortness {
        Short,
        NotShort,

        /**
         * Neither duration nor dimensions were available. The caller stores [ContentKind.Undetermined]
         * and reclassifies once a player reports real values.
         */
        Undetermined,
    }

    public fun classify(status: Status): ContentKind {
        val displayed = status.displayed
        val attachments = displayed.mediaAttachments
        val videoLike = attachments.filter { it.type.isVideoLike }
        return when {
            attachments.isEmpty() -> displayed.withoutMedia()
            attachments.all { it.type == AttachmentKind.Audio } -> ContentKind.Audio
            videoLike.size == 1 && attachments.size == 1 -> shortness(videoLike.single(), displayed).kind
            videoLike.isNotEmpty() -> ContentKind.Video
            attachments.all { it.type == AttachmentKind.Image } -> ContentKind.Photo
            else -> ContentKind.Text
        }
    }

    private val AttachmentKind.isVideoLike: Boolean get() = this == AttachmentKind.Video || this == AttachmentKind.Gifv

    /** A post without media is news when it carries a link card, text otherwise. */
    private fun Status.withoutMedia(): ContentKind = if (card != null) ContentKind.News else ContentKind.Text

    private val Shortness.kind: ContentKind
        get() = when (this) {
            Shortness.Short -> ContentKind.Short
            Shortness.NotShort -> ContentKind.Video
            Shortness.Undetermined -> ContentKind.Undetermined
        }

    /**
     * A shorts hashtag is the author saying what they made: it promotes a clip whose shape says
     * otherwise, but is checked after the duration ceiling, so it never rescues a clip that is too long.
     */
    public fun shortness(attachment: MediaAttachment, status: Status): Shortness {
        val duration = attachment.duration
        val tagged = status.tags.any { it.name.lowercase() in shortHashtags }
        return when {
            duration != null && duration > MAXIMUM_SHORT_DURATION -> Shortness.NotShort
            tagged -> Shortness.Short
            duration != null && duration <= UNCONDITIONAL_SHORT_DURATION -> Shortness.Short
            else -> byShape(hasDuration = duration != null, aspect = attachment.aspectRatio)
        }
    }

    /**
     * Between the two duration ceilings shape decides. Without a duration, landscape is still never a
     * short, but portrait needs a duration to settle.
     */
    private fun byShape(hasDuration: Boolean, aspect: Double?): Shortness = when {
        aspect == null -> Shortness.Undetermined
        aspect > MAXIMUM_SHORT_ASPECT -> Shortness.NotShort
        hasDuration -> Shortness.Short
        else -> Shortness.Undetermined
    }

    /**
     * Reclassifies once a player has reported the real [observed] duration and dimensions of an
     * attachment the server described incompletely.
     */
    public fun reclassify(status: Status, attachmentId: String, observed: MediaDimensions): ContentKind {
        val displayed = status.displayed
        val patched = displayed.copy(
            mediaAttachments = displayed.mediaAttachments.map { attachment ->
                if (attachment.id == attachmentId) attachment.observed(observed) else attachment
            },
        )
        return classify(patched)
    }

    private fun MediaAttachment.observed(observed: MediaDimensions): MediaAttachment {
        val meta = meta ?: MediaMeta()
        val width = observed.width
        val height = observed.height
        val original = (meta.original ?: MediaDimensions()).copy(
            duration = observed.duration,
            width = width,
            height = height,
            aspect = if (width != null && height != null && height > 0) width.toDouble() / height else null,
        )
        return copy(meta = meta.copy(original = original))
    }

    /** The aspect ratio to lay out in before anything has loaded, so nothing shifts when the bytes arrive. */
    public fun layoutAspect(attachments: List<MediaAttachment>): Double = when {
        attachments.isEmpty() -> MediaAttachment.DEFAULT_ASPECT
        attachments.size > 1 -> 1.0
        else -> attachments.first().displayAspectRatio
    }
}
