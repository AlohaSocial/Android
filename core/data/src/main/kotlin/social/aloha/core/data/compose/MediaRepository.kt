// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.MediaEndpoints

/** A file of the app's own to upload: where it is, what it is called, what it is, and its description. */
public data class UploadFile(val file: File, val name: String, val mime: String, val description: String?)

/**
 * What a post attaches, on the reader's server: uploaded, waited for while the server converts it,
 * and described. The composer and both workers go through here.
 */
@Singleton
public class MediaRepository @Inject constructor(private val clients: ClientFactory) {
    /**
     * Uploads [upload]: `POST /api/v2/media`, the older route on a server without it, then asked
     * after until the server has converted it.
     * [onSent] hears how much has gone, [onProcessing] that the server is converting it. The
     * attachment as last seen, without a `url` when the wait ran out first. A PHP upload limit
     * answers 413 with an HTML page, which is a refusal like any other, not worth trying again.
     */
    public suspend fun upload(
        reader: SignedInAccount,
        upload: UploadFile,
        onSent: (Long, Long) -> Unit = { _, _ -> },
        onProcessing: suspend () -> Unit = {},
    ): Answer<MediaAttachment> {
        val request = { v2: Boolean ->
            MediaEndpoints.upload(upload.file, upload.name, upload.mime, upload.description, v2, onSent)
        }
        var answer = clients.answer(reader, request(true))
        if ((answer as? Answer.Missed)?.error == ApiError.NotFound) answer = clients.answer(reader, request(false))
        val uploaded = (answer as? Answer.Got)?.value
        return when {
            answer is Answer.Missed -> Answer.Missed(refusal(answer.error))

            uploaded == null || uploaded.url != null -> answer

            else -> {
                onProcessing()
                converted(reader, uploaded.id)
            }
        }
    }

    /** Gives medium [id] its [description] and, when given, its focal point, `x,y` in −1…1. */
    public suspend fun describe(
        reader: SignedInAccount,
        id: String,
        description: String,
        focus: Pair<Float, Float>?,
    ): Answer<MediaAttachment> = clients.answer(
        reader,
        focus?.let { (x, y) -> MediaEndpoints.updateFocus(id, x.toDouble(), y.toDouble(), description) }
            ?: MediaEndpoints.updateDescription(id, description),
    )

    /** Asks after [id] ever less often until it has a `url`, the wait runs out, or the server refuses. */
    private suspend fun converted(reader: SignedInAccount, id: String): Answer<MediaAttachment> {
        var last: Answer<MediaAttachment> = Answer.Missed(ApiError.NotFound)
        var wait = FIRST_POLL_MILLIS
        var waited = 0L
        while (waited < PROCESSING_MILLIS) {
            delay(wait)
            waited += wait
            wait = (wait * 2).coerceAtMost(MAX_POLL_MILLIS)
            val now = clients.answer(reader, MediaEndpoints.media(id))
            // no answer is worth waiting through; any other failure is final
            val settled = (now is Answer.Got && now.value.url != null) ||
                (now is Answer.Missed && now.error !is ApiError.Transport)
            if (now is Answer.Got || settled) last = now
            if (settled) break
        }
        return last
    }

    private fun refusal(error: ApiError): ApiError =
        if (error is ApiError.Server && error.status == TOO_LARGE) ApiError.Unprocessable(null) else error

    private companion object {
        const val TOO_LARGE = 413
        const val FIRST_POLL_MILLIS = 1_000L
        const val MAX_POLL_MILLIS = 15_000L
        const val PROCESSING_MILLIS = 10 * 60_000L
    }
}
