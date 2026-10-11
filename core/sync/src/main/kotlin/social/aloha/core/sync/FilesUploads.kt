// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where one upload into Nextcloud Files stands. */
public sealed interface FilesUploadState {
    /** Waiting for a network, or for another try. */
    public data object Queued : FilesUploadState

    /** Going out; [fraction] of the file has gone, when it is known. */
    public data class Sending(val fraction: Float?) : FilesUploadState

    /** Saved at [path], from the root of the person's files; the name may have become `name (2).ext`. */
    public data class Done(val path: String) : FilesUploadState

    /** Refused, or not sent after trying again; nothing of it is left on the Nextcloud. */
    public data object Failed : FilesUploadState

    /** Stopped from its notification. */
    public data object Cancelled : FilesUploadState
}

/** One upload into a folder: its [id], the [fileName] it was asked to be saved as, and where it stands. */
public data class FilesUpload(val id: UUID, val fileName: String, val state: FilesUploadState)

/**
 * Uploads into a person's Nextcloud Files that survive the app: each is a WorkManager job, in the
 * foreground with its progress in the upload notification, waiting for a network when there is none
 * and carrying on from the chunks already sent when it is tried again. The Files browser only enqueues
 * and watches, by folder, so it can show what is on its way into the folder it shows.
 */
@Singleton
public class FilesUploads @Inject constructor(@ApplicationContext private val context: Context) {
    private val work get() = WorkManager.getInstance(context)

    /**
     * Saves [file], the app's own copy, which is deleted once it is no longer needed, as [fileName] in
     * [folder] of account [accountId]'s files.
     */
    public suspend fun enqueue(accountId: String, file: File, fileName: String, folder: String): UUID {
        val request = OneTimeWorkRequestBuilder<FilesUploadWorker>()
            .setInputData(
                workDataOf(
                    FilesUploadWorker.ACCOUNT to accountId,
                    FilesUploadWorker.PATH to file.absolutePath,
                    FilesUploadWorker.NAME to fileName,
                    FilesUploadWorker.FOLDER to folder,
                    // the upload folder its chunks gather in, kept across tries so a retry carries on
                    FilesUploadWorker.UPLOAD_ID to "aloha-${UUID.randomUUID()}",
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .addTag(folderTag(accountId, folder))
            .addTag(NAME_TAG + fileName)
            .build()
        work.enqueue(request).await()
        return request.id
    }

    /** The uploads into [folder] of account [accountId]'s files WorkManager still knows of, about a day's worth. */
    public fun observe(accountId: String, folder: String): Flow<List<FilesUpload>> =
        work.getWorkInfosByTagFlow(folderTag(accountId, folder)).map { infos -> infos.map { it.upload() } }

    public fun cancel(id: UUID) {
        work.cancelWorkById(id)
    }

    private fun WorkInfo.upload(): FilesUpload {
        val fileName = tags.firstOrNull { it.startsWith(NAME_TAG) }?.removePrefix(NAME_TAG).orEmpty()
        val state = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> FilesUploadState.Queued

            WorkInfo.State.RUNNING ->
                FilesUploadState.Sending(progress.getFloat(FilesUploadWorker.PROGRESS, -1f).takeIf { it >= 0f })

            WorkInfo.State.SUCCEEDED -> FilesUploadState.Done(outputData.getString(FilesUploadWorker.SAVED).orEmpty())

            WorkInfo.State.FAILED -> FilesUploadState.Failed

            WorkInfo.State.CANCELLED -> FilesUploadState.Cancelled
        }
        return FilesUpload(id, fileName, state)
    }

    private fun folderTag(accountId: String, folder: String) = "$TAG:$accountId:${folder.trim('/')}"

    private companion object {
        const val TAG = "files-upload"
        const val NAME_TAG = "files-upload-name:"
        val BACKOFF: Duration = Duration.ofSeconds(15)
    }
}
