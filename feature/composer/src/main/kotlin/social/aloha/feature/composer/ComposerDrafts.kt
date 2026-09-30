// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import social.aloha.core.data.compose.DraftMedia
import social.aloha.core.data.compose.DraftPoll
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.DraftSegment
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.OutboxEntry
import social.aloha.core.sync.UploadState

/** What opening the composer on a draft found. */
internal sealed interface Opened {
    /** A new post, or a draft to carry on with. */
    data class Writing(val entry: OutboxEntry?) : Opened

    /** The post is going out right now and cannot be changed. */
    data object Sending : Opened
}

/**
 * The post being written as a draft in the [Outbox]: kept a moment after each change, and when the
 * composer closes with something written, so leaving never loses it; forgotten once posted or
 * discarded. Writing on leaving runs in [scope], which outlives the composer.
 */
internal class DraftKeeper(private val outbox: Outbox, private val scope: CoroutineScope, draftId: String?) {
    val id: String = draftId ?: UUID.randomUUID().toString()
    private var finished = false
    private var saved = draftId != null

    /** Takes the draft back from the outbox for editing. */
    suspend fun open(): Opened {
        if (!saved) return Opened.Writing(null)
        if (!outbox.reopen(id)) return Opened.Sending
        return Opened.Writing(outbox.get(id))
    }

    /** Keeps [post] as the draft of [accountId]; nothing written forgets it. */
    suspend fun save(accountId: String?, post: DraftPost?) {
        if (finished || accountId == null) return
        if (post != null) {
            outbox.saveDraft(id, accountId, post)
            saved = true
        } else if (saved) {
            outbox.delete(id, files = false)
            saved = false
        }
    }

    /** The composer closes: [post] is kept, when there is one; whether its files must stay for it. */
    fun leave(accountId: String?, post: DraftPost?): Boolean {
        if (finished || accountId == null || post == null) return false
        finished = true
        scope.launch { outbox.saveDraft(id, accountId, post) }
        return true
    }

    /** The writer let the post go; its files go with the composer. */
    fun discard() = forget()

    /** The post is out; the draft is no more. */
    fun posted() = forget()

    private fun forget() {
        finished = true
        if (saved) scope.launch { outbox.delete(id, files = false) }
    }
}

/**
 * The post as a draft, from this state and what was typed: [texts] of each segment, the [spoiler]
 * (sent only while shown), answering [replyToId], with [media] of each segment.
 */
internal fun ComposerUiState.draft(
    texts: List<String>,
    spoiler: String,
    replyToId: String?,
    media: List<List<Attachment>> = attachments,
) = DraftPost(
    segments = texts.mapIndexed { index, text ->
        DraftSegment(text, media.getOrElse(index) { emptyList() }.map(Attachment::toDraft))
    },
    replyToId = replyToId,
    spoiler = spoiler.trim().takeIf { spoilerShown && it.isNotEmpty() },
    visibility = visibility,
    language = language,
    quotePolicy = quotePolicy.wire,
    mediaSensitive = mediaSensitive,
    poll = poll?.let { DraftPoll(it.choices, it.seconds, it.multiple, it.hideTotals) },
    scheduledAt = scheduledAt,
)

/** This state with the choices [draft] was left with, where they are still allowed. */
internal fun ComposerUiState.restored(draft: DraftPost) = copy(
    visibility = draft.visibility.takeIf { it in visibilities } ?: visibility,
    language = draft.language,
    spoilerShown = draft.spoiler != null,
    quotePolicy = QuotePolicy.entries.firstOrNull { it.wire == draft.quotePolicy } ?: QuotePolicy.Anyone,
    posted = draft.postedIds.size,
)

/** Puts back the attachments [draft] kept, each in its post: uploaded already, or uploading again. */
internal fun Attachments.restore(draft: DraftPost) {
    sensitive.value = draft.mediaSensitive
    resize(draft.segments.size)
    draft.segments.forEachIndexed { index, segment -> segment.media.forEach { put(index, it.toAttachment()) } }
}

internal fun DraftPoll.toUi() =
    PollUi(options.plus(List((2 - options.size).coerceAtLeast(0)) { "" }), seconds, multiple, hideTotals)

private fun Attachment.toDraft() = DraftMedia(
    fileName = fileName,
    mimeType = mimeType,
    path = file?.absolutePath,
    mediaId = mediaId,
    previewUrl = previewUrl,
    description = description,
    focusX = focus?.x,
    focusY = focus?.y,
)

/** The attachment a draft kept, uploaded already or waiting to be. */
internal fun DraftMedia.toAttachment(): Attachment {
    val focus = focus?.let { (x, y) -> Focus(x, y) }
    return Attachment(
        UUID.randomUUID().toString(),
        path?.let(::File),
        fileName,
        mimeType,
        upload = mediaId?.let { UploadState.Done(it, previewUrl) } ?: UploadState.Queued,
        description = description,
        // what the server has is not kept, so the description and focus are sent again before posting
        focus = focus,
    )
}
