// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import social.aloha.core.model.SignedInAccount

/** A file on this device, copied out of the picker so it outlives the app, waiting to be uploaded. */
public data class LocalMedia(val file: File, val fileName: String, val mimeType: String, val description: String?)

/** Where one upload stands. */
public sealed interface UploadState {
    /** Waiting for a network, or for its turn. */
    public data object Queued : UploadState

    /** Going out; [fraction] of the file has gone, when the size is known. */
    public data class Sending(val fraction: Float?) : UploadState

    /** On the server, which is still converting it (a video, mostly); a post cannot attach it yet. */
    public data object Processing : UploadState

    /** Ready to attach as [mediaId]; [previewUrl] is the server's own preview of it. */
    public data class Done(val mediaId: String, val previewUrl: String?) : UploadState

    /** The server would not take it and said why, when it did; nothing is retried. */
    public data class Refused(val message: String?) : UploadState

    /** It could not be sent after retrying; asking again starts over. */
    public data object Failed : UploadState

    /** Stopped from its notification before it was done; asking again starts over. */
    public data object Cancelled : UploadState
}

/**
 * Uploads that survive the app: each is a WorkManager job, in the foreground with its progress in a
 * notification, waiting for a network when there is none and retried with backoff when the server
 * does not answer. The composer only enqueues and watches.
 *
 * Each file of each account is one upload, named after both: a draft reopened while its file is still
 * going out watches that upload instead of starting a second, one that went out meanwhile is taken
 * as it is, one cancelled meanwhile stays so until asked again, and one that failed is tried again.
 * Mastodon has no idempotency key and no resumable upload for media, so this is the only guard
 * against sending a file twice; an upload cut off half way starts over. WorkManager forgets a
 * finished upload after about a day, which is about when Mastodon deletes media no post attached.
 */
@Singleton
public class MediaUploads @Inject constructor(@ApplicationContext private val context: Context) {
    private val work get() = WorkManager.getInstance(context)

    /** The name the upload of [file] as account [accountId] goes by, which watches and cancels it. */
    public fun name(accountId: String, file: File): String = "$TAG:$accountId:${file.absolutePath}"

    /**
     * Uploads [media] as [account]: from the start, replacing any upload of it, or when [join], into
     * the upload of it still going, done already or cancelled, starting one only when there is none.
     * Returns once WorkManager has it, so watching it never sees the upload it replaced.
     */
    public suspend fun enqueue(account: SignedInAccount, media: LocalMedia, join: Boolean = false): String {
        val name = name(account.id, media.file)
        val before = work.getWorkInfosForUniqueWorkFlow(name).first().latest()?.state
        if (join && before in setOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED)) return name
        val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setInputData(
                workDataOf(
                    MediaUploadWorker.ACCOUNT to account.id,
                    MediaUploadWorker.PATH to media.file.absolutePath,
                    MediaUploadWorker.NAME to media.fileName,
                    MediaUploadWorker.MIME to media.mimeType,
                    MediaUploadWorker.DESCRIPTION to media.description,
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            // straight away when the system allows it, as ordinary work when the quota is spent
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        work.enqueueUniqueWork(name, if (join) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
            .await()
        return name
    }

    /** Where the upload [name] stands; queued until it is enqueued. */
    public fun observe(name: String): Flow<UploadState> = work.getWorkInfosForUniqueWorkFlow(name).map {
        it.latest()?.state() ?: UploadState.Queued
    }

    public fun cancel(name: String) {
        work.cancelUniqueWork(name)
    }

    /** The upload a name stands for: replacing one deletes it, so there is one, or one still going. */
    private fun List<WorkInfo>.latest(): WorkInfo? = firstOrNull { !it.state.isFinished } ?: lastOrNull()

    private fun WorkInfo.state(): UploadState = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UploadState.Queued

        WorkInfo.State.RUNNING -> when {
            progress.getBoolean(MediaUploadWorker.PROCESSING, false) -> UploadState.Processing
            else -> UploadState.Sending(progress.getFloat(MediaUploadWorker.PROGRESS, -1f).takeIf { it >= 0f })
        }

        WorkInfo.State.SUCCEEDED -> UploadState.Done(
            outputData.getString(MediaUploadWorker.MEDIA_ID).orEmpty(),
            outputData.getString(MediaUploadWorker.PREVIEW),
        )

        WorkInfo.State.FAILED -> if (outputData.getBoolean(MediaUploadWorker.REFUSED, false)) {
            UploadState.Refused(outputData.getString(MediaUploadWorker.MESSAGE))
        } else {
            UploadState.Failed
        }

        WorkInfo.State.CANCELLED -> UploadState.Cancelled
    }

    private companion object {
        const val TAG = "media-upload"
        val BACKOFF: Duration = Duration.ofSeconds(15)
    }
}
