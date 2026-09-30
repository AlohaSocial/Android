// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why an edit could not be made. */
internal sealed interface EditFailure {
    /** Trimmed and scaled as asked, the video is still over the server's ceiling. */
    data class StillTooLarge(val limitBytes: Long) : EditFailure

    /** The video or picture could not be edited; it stays as it was. */
    data object Failed : EditFailure
}

/**
 * Changes made to an attachment after it was picked: a filter on a picture, a trim or a smaller size
 * on a video. Each is made on a copy, from the file as picked, and uploaded in the attachment's place.
 */
internal class MediaEdits(
    private val attachments: Attachments,
    private val preparation: MediaPreparation,
    private val videos: VideoTransformer,
    private val scope: CoroutineScope,
) {
    private val failures = MutableStateFlow<EditFailure?>(null)
    val failure: StateFlow<EditFailure?> = failures.asStateFlow()

    fun onFailureShown() {
        failures.value = null
    }

    /** Bakes [filter] into picture [id], from the picture as picked; the description carries over. */
    fun applyFilter(id: String, filter: PhotoFilter) {
        val attachment = attachments[id]?.takeIf { it.filterable && it.filter != filter } ?: return
        val original = attachment.source ?: return
        val picked = Picked(original, attachment.fileName, attachment.mimeType)
        attachments.change(id) { it.copy(preparing = true) }
        scope.launch {
            val result = if (filter == PhotoFilter.Original) {
                picked
            } else {
                withContext(Dispatchers.IO) { preparation.filtered(picked, filter) }
            }
            if (result == null) {
                attachments.change(id) { it.copy(preparing = false) }
                failures.value = EditFailure.Failed
                return@launch
            }
            attachments.replace(id, result, keepFile = attachment.file == original) {
                it.copy(original = original, filter = filter)
            }
        }
    }

    /** What video [id] is: its length and frame, which the trim and the sizes are chosen from. */
    suspend fun info(id: String): VideoInfo? {
        val attachment = attachments[id]?.takeIf { it.isVideo } ?: return null
        val source = attachment.source ?: return null
        return withContext(Dispatchers.IO) { videos.info(source) }
    }

    /**
     * Trims and scales video [id] by [edit], from the video as picked, and uploads the result in its
     * place when it fits the server; one that still does not fit waits for another try.
     */
    fun editVideo(id: String, edit: VideoEdit) {
        val attachment = attachments[id]?.takeIf { it.isVideo } ?: return
        val original = attachment.source ?: return
        attachments.change(id) { it.copy(preparing = true) }
        scope.launch {
            val limit = attachments.account?.capabilities?.limits?.videoSizeLimit ?: Long.MAX_VALUE
            val result = preparation.video(Picked(original, attachment.fileName, attachment.mimeType), edit)
            when {
                result == null -> {
                    attachments.change(id) { it.copy(preparing = false) }
                    failures.value = EditFailure.Failed
                }

                result.file.length() > limit -> {
                    result.file.delete()
                    attachments.change(id) { it.copy(preparing = false) }
                    failures.value = EditFailure.StillTooLarge(limit)
                }

                else -> attachments.replace(id, result, keepFile = attachment.file == original) {
                    it.copy(original = original)
                }
            }
        }
    }
}
