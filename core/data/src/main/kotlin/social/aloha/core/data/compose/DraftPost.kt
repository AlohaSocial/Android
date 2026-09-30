// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.InstantSerializer
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility
import social.aloha.core.network.endpoints.MediaAttribute
import social.aloha.core.network.endpoints.StatusPost

/**
 * A post, or a thread of them, as the writer left it: kept as a draft, or waiting to be sent. Once
 * it is being sent each segment carries the text that goes out (its games played) and its own
 * idempotency key, so sending again after a failure makes no second post; [postedIds] are the
 * segments already out, which sending again starts after.
 */
@Serializable
public data class DraftPost(
    val segments: List<DraftSegment> = listOf(DraftSegment()),
    val replyToId: String? = null,
    /** The content warning, when shown; it goes with every segment. */
    val spoiler: String? = null,
    val visibility: Visibility = Visibility.Public,
    val language: String? = null,
    /** Who may quote the opening post, as the server names it; null for anyone. */
    val quotePolicy: String? = null,
    val mediaSensitive: Boolean = false,
    val poll: DraftPoll? = null,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant? = null,
    val postedIds: List<String> = emptyList(),
    /** The writer's own post this one writes again, deleted only once this one is out. */
    val replaces: String? = null,
) {
    /**
     * Segment [index] as the server takes it, answering [inReplyToId]; the poll and time go with the
     * first. An edit carries the media's descriptions with it, as attached media cannot be changed alone.
     */
    public fun statusPost(index: Int, inReplyToId: String?, editing: Boolean = false): StatusPost {
        val segment = segments[index]
        val mediaIds = segment.media.mapNotNull(DraftMedia::mediaId)
        val poll = poll.takeIf { index == 0 }
        return StatusPost(
            text = segment.sent ?: segment.text,
            visibility = visibility,
            spoilerText = spoiler,
            // a content warning hides the text, so the post is sensitive; media may be on their own
            sensitive = spoiler != null || (mediaSensitive && mediaIds.isNotEmpty()),
            language = language,
            inReplyToId = inReplyToId,
            mediaIds = mediaIds,
            pollOptions = poll?.options.orEmpty(),
            pollExpiresInSeconds = poll?.seconds,
            pollMultiple = poll?.multiple == true,
            pollHideTotals = poll?.hideTotals == true,
            scheduledAt = scheduledAt.takeIf { index == 0 },
            idempotencyKey = requireNotNull(segment.key) { "a segment is keyed before it is sent" },
            quotePolicy = quotePolicy.takeIf { index == 0 },
            mediaAttributes = if (editing) segment.media.mapNotNull(DraftMedia::attribute) else emptyList(),
        )
    }

    /** The app's own copies of the files attached, which go when the post does. */
    public val files: List<File> get() = segments.flatMap { it.media }.mapNotNull { it.path?.let(::File) }
}

/** One post of the thread. */
@Serializable
public data class DraftSegment(
    val text: String = "",
    val media: List<DraftMedia> = emptyList(),
    /** The text that goes out, its games played; set once sending begins. */
    val sent: String? = null,
    val key: String? = null,
)

/**
 * A picture, video or file: the app's copy at [path], uploaded as [mediaId] when it has been; one
 * the server made itself, a GIF or a Nextcloud file, has only the id and the server's preview.
 */
@Serializable
public data class DraftMedia(
    val fileName: String,
    val mimeType: String,
    val path: String? = null,
    val mediaId: String? = null,
    val previewUrl: String? = null,
    val description: String = "",
    val focusX: Float? = null,
    val focusY: Float? = null,
) {
    /** The focal point, `x,y` in −1…1, when one was set. */
    val focus: Pair<Float, Float>?
        get() {
            val x = focusX
            val y = focusY
            return if (x != null && y != null) x to y else null
        }
}

@Serializable
public data class DraftPoll(
    val options: List<String>,
    val seconds: Long,
    val multiple: Boolean = false,
    val hideTotals: Boolean = false,
)

private fun DraftMedia.attribute(): MediaAttribute? = mediaId?.let { id ->
    MediaAttribute(id, description, focus?.let { (x, y) -> x.toDouble() to y.toDouble() })
}

/**
 * [status] as a post to write again: its [text] and [spoiler] as its author wrote them (the source,
 * not the rendered content), its media by the ids the server keeps, and its poll lasting as long as
 * it did, within what the server allows by then.
 */
public fun draftOf(status: Status, text: String, spoiler: String): DraftPost = DraftPost(
    segments = listOf(
        DraftSegment(
            text,
            status.mediaAttachments.map { media ->
                DraftMedia(
                    fileName = media.url?.substringAfterLast('/')?.substringBefore('?') ?: media.id,
                    mimeType = mimeTypeOf(media),
                    mediaId = media.id,
                    previewUrl = media.previewUrl ?: media.url,
                    description = media.description.orEmpty(),
                )
            },
        ),
    ),
    replyToId = status.inReplyToId,
    spoiler = spoiler.takeIf { it.isNotBlank() },
    visibility = status.visibility,
    language = status.language,
    mediaSensitive = status.sensitive && status.mediaAttachments.isNotEmpty() && spoiler.isBlank(),
    poll = status.poll?.let { poll ->
        val lasted = poll.expiresAt?.let { Duration.between(status.createdAt, it).seconds }
        DraftPoll(poll.options.map { it.title }, lasted ?: DAY_SECONDS, poll.multiple)
    },
)

/** What kind of file the server made [media] from, as far as its type tells. */
public fun mimeTypeOf(media: MediaAttachment): String = when (media.type) {
    AttachmentKind.Image -> "image/jpeg"
    AttachmentKind.Gifv, AttachmentKind.Video -> "video/mp4"
    AttachmentKind.Audio -> "audio/mpeg"
    else -> "application/octet-stream"
}

private const val DAY_SECONDS = 86_400L
