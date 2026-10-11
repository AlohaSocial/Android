// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.nextcloud.NextcloudFiles
import social.aloha.core.nextcloud.NextcloudUpload
import timber.log.Timber

/**
 * Saves one file into one person's Nextcloud Files, as foreground work with its progress in the upload
 * notification. No answer or a busy server is tried again a few times, carrying on from the chunks the
 * server has; a refusal is final. Once it is over, saved or not, the app's copy of the file goes, and
 * so do the chunks of an upload that will not be carried on.
 */
@HiltWorker
internal class FilesUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val files: NextcloudFiles,
) : CoroutineWorker(context, params) {
    private val notifications = UploadNotifications(context)
    private val progress = ProgressThrottle { percent, fraction ->
        setProgressAsync(workDataOf(PROGRESS to fraction))
        setForegroundAsync(notifications.info(id, name(), percent))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.info(id, name(), percent = null)

    override suspend fun doWork(): Result {
        val account = inputData.getString(ACCOUNT)?.let { accounts.byId(it) }
        val upload = upload()
        if (account == null || upload == null) {
            inputData.getString(PATH)?.let(::File)?.delete()
            return Result.failure()
        }
        tryForeground()
        return try {
            when (val saved = files.upload(account, upload, progress::onSent)) {
                is ApiResult.Success -> {
                    upload.source.delete()
                    Result.success(workDataOf(SAVED to saved.value))
                }

                is ApiResult.Failure -> failure(account, upload, saved.error)
            }
        } finally {
            // stopped from its notification: nothing of it is to stay, here or on the Nextcloud
            if (isStopped) withContext(NonCancellable) { if (cancelled()) giveUp(account, upload) }
        }
    }

    private fun name() = inputData.getString(NAME) ?: "upload"

    /**
     * Stopped because it was cancelled, not because its network went away, after which it runs again;
     * `stopReason` would say so too, from Android 12 on only.
     */
    private suspend fun cancelled(): Boolean =
        WorkManager.getInstance(applicationContext).getWorkInfoByIdFlow(id).first()?.state == WorkInfo.State.CANCELLED

    /** The upload the input asks for, while the app's copy of the file is still there. */
    private fun upload(): NextcloudUpload? {
        val file = inputData.getString(PATH)?.let(::File)?.takeIf(File::exists)
        val folder = inputData.getString(FOLDER)
        val id = inputData.getString(UPLOAD_ID)
        return if (file != null && folder != null && id != null) NextcloudUpload(folder, name(), file, id) else null
    }

    private suspend fun failure(account: SignedInAccount, upload: NextcloudUpload, error: ApiError): Result {
        val kind = error.javaClass.simpleName
        Timber.tag(LogArea.Media.name).i("Files upload attempt %d failed: %s", runAttemptCount, kind)
        val again = error is ApiError.Server || error is ApiError.Transport || error is ApiError.RateLimited
        if (again && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        giveUp(account, upload)
        return Result.failure()
    }

    private suspend fun giveUp(account: SignedInAccount, upload: NextcloudUpload) {
        files.abandon(account, upload.id)
        upload.source.delete()
    }

    companion object {
        const val ACCOUNT = "account"
        const val PATH = "path"
        const val NAME = "name"
        const val FOLDER = "folder"
        const val UPLOAD_ID = "upload_id"
        const val PROGRESS = "progress"
        const val SAVED = "saved"

        private const val MAX_ATTEMPTS = 5
    }
}
