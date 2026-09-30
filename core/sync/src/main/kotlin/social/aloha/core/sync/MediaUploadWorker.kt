// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.UploadFile
import social.aloha.core.network.ApiError

/**
 * Uploads one file for one account, through [MediaRepository], as foreground work with its progress
 * in a notification; the file is the app's own copy, so it is there however long the wait for a
 * network. No answer or a busy server is tried again a few times; a refusal is final.
 */
@HiltWorker
internal class MediaUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val media: MediaRepository,
) : CoroutineWorker(context, params) {
    private val notifications = UploadNotifications(context)
    private var shownPercent = -1

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.info(id, name(), percent = null)

    override suspend fun doWork(): Result {
        val account = inputData.getString(ACCOUNT)?.let { accounts.byId(it) }
        val file = inputData.getString(PATH)?.let(::File)?.takeIf(File::exists)
        if (account == null || file == null) return failed(refused = true, message = null)
        tryForeground()
        val mime = inputData.getString(MIME) ?: "application/octet-stream"
        val result = media.upload(
            account,
            UploadFile(file, name(), mime, inputData.getString(DESCRIPTION)),
            onSent = ::onSent,
            onProcessing = { setProgress(workDataOf(PROCESSING to true)) },
        )
        return when {
            result is Answer.Missed -> failure(result.error)

            (result as Answer.Got).value.url == null -> failed(refused = false, message = null)

            else -> Result.success(
                workDataOf(MEDIA_ID to result.value.id, PREVIEW to (result.value.previewUrl ?: result.value.url)),
            )
        }
    }

    private fun name() = inputData.getString(NAME) ?: "upload"

    private fun onSent(sent: Long, total: Long) {
        if (total <= 0) return
        // every few percent: each step is a progress write, a notification and a redrawn composer
        val percent = (sent * PERCENT / total).toInt() / STEP * STEP
        if (percent == shownPercent) return
        shownPercent = percent
        setProgressAsync(workDataOf(PROGRESS to sent.toFloat() / total))
        setForegroundAsync(notifications.info(id, name(), percent))
    }

    private fun failure(error: ApiError): Result = when (error) {
        is ApiError.Unprocessable -> failed(refused = true, message = error.message)
        is ApiError.Server, is ApiError.Transport, is ApiError.RateLimited -> retry()
        else -> failed(refused = true, message = null)
    }

    private fun retry(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else failed(false, null)

    private fun failed(refused: Boolean, message: String?): Result =
        Result.failure(workDataOf(REFUSED to refused, MESSAGE to message))

    companion object {
        const val ACCOUNT = "account"
        const val PATH = "path"
        const val NAME = "name"
        const val MIME = "mime"
        const val DESCRIPTION = "description"
        const val PROGRESS = "progress"
        const val PROCESSING = "processing"
        const val MEDIA_ID = "media_id"
        const val PREVIEW = "preview"
        const val REFUSED = "refused"
        const val MESSAGE = "message"

        private const val PERCENT = 100
        private const val STEP = 5
        private const val MAX_ATTEMPTS = 5
    }
}
