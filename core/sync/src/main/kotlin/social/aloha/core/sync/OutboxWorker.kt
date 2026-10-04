// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.DraftMedia
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.OutboxEntry
import social.aloha.core.data.compose.PostSender
import social.aloha.core.data.compose.UploadFile
import social.aloha.core.data.map
import social.aloha.core.model.LogArea
import social.aloha.core.model.OutboxState
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import timber.log.Timber

/**
 * Sends one account's queued posts, oldest first. Each is first made whole on the server: what never
 * uploaded is uploaded, and media the server has let go of since (it drops what no post took after a
 * while) are uploaded again from the app's copies. Then the segments not yet out are posted, each
 * with the idempotency key it was given when queued, so a post the server made before an answer was
 * lost is not made twice.
 *
 * No answer or a busy server puts the post back and tries again later; a refusal sets it aside for
 * the writer, with the server's reason, and the queue goes on; a revoked sign-in pauses the queue.
 */
@HiltWorker
internal class OutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val media: MediaRepository,
    private val outbox: Outbox,
    private val sender: PostSender,
) : CoroutineWorker(context, params) {
    private val notifications = OutboxNotifications(context)

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.sending(id)

    override suspend fun doWork(): Result {
        val account = inputData.getString(ACCOUNT)?.let { accounts.byId(it) } ?: return Result.success()
        var outcome: Result? = null
        var foreground = false
        while (outcome == null) {
            val entry = outbox.claim(account.id)
            // the notification only once there is something to send; a drain that finds nothing stays silent
            if (entry != null && !foreground) {
                tryForeground()
                foreground = true
            }
            outcome = if (entry == null) Result.success() else settle(account, entry, send(account, entry))
        }
        return outcome
    }

    /** Where [entry] goes after sending stopped at [error]; the result the work ends with, or null to go on. */
    private suspend fun settle(account: SignedInAccount, entry: OutboxEntry, error: ApiError?): Result? = when {
        error == null -> {
            outbox.sent(entry.id)
            null
        }

        error is ApiError.Unauthorised -> {
            Timber.tag(LogArea.Compose.name).i("Outbox of %s paused: token refused", account.id)
            outbox.settle(entry.id, OutboxState.Paused)
            outbox.setPaused(account.id, paused = true)
            notifications.paused(account.id, account.qualifiedHandle)
            Result.success()
        }

        // worth another try, a few times: one post that never goes must not hold the queue for ever
        error.isWorthRetrying && runAttemptCount < MAX_ATTEMPTS -> {
            Timber.tag(LogArea.Compose.name).i("Post %s not sent, retried: %s", entry.id, error.javaClass.simpleName)
            outbox.settle(entry.id)
            Result.retry()
        }

        else -> {
            val message = (error as? ApiError.Unprocessable)?.message
            Timber.tag(LogArea.Compose.name).w("Post %s not sent, given up: %s", entry.id, error.javaClass.simpleName)
            outbox.settle(entry.id, OutboxState.Failed, message)
            notifications.refused(account.id, entry.id, message)
            null
        }
    }

    /** Uploads what [entry] needs and sends it; the error that stopped it, or null once it is out. */
    private suspend fun send(account: SignedInAccount, entry: OutboxEntry): ApiError? {
        var post = entry.post
        for (index in post.postedIds.size..post.segments.lastIndex) {
            val uploaded = post.segments[index].media.map { item ->
                when (val made = uploaded(account, item)) {
                    is Answer.Got -> made.value
                    is Answer.Missed -> return made.error
                }
            }
            if (uploaded != post.segments[index].media) {
                post =
                    post.copy(
                        segments = post.segments.toMutableList().also {
                            it[index] =
                                it[index].copy(media = uploaded)
                        },
                    )
                outbox.progress(entry.id, post)
            }
        }
        return sender.send(account, post) { outbox.progress(entry.id, it) }.error
    }

    /**
     * [item] as the server has it: its description and focus sent again when it is there, uploaded
     * from the app's copy when it never was or the server let go of it.
     */
    private suspend fun uploaded(account: SignedInAccount, item: DraftMedia): Answer<DraftMedia> {
        val there = item.mediaId?.let { media.describe(account, it, item.description, item.focus) }
        return if (there == null || (there as? Answer.Missed)?.error == ApiError.NotFound) {
            upload(account, item)
        } else {
            there.map { item }
        }
    }

    private suspend fun upload(account: SignedInAccount, item: DraftMedia): Answer<DraftMedia> {
        val file = item.path?.let(::File)?.takeIf(File::exists)
        val upload = file?.let {
            media.upload(account, UploadFile(it, item.fileName, item.mimeType, item.description.ifBlank { null }))
        } ?: Answer.Missed(ApiError.Unprocessable(null))
        val attachment = (upload as? Answer.Got)?.value
        val fresh = attachment?.let { item.copy(mediaId = it.id, previewUrl = it.previewUrl) }
        val focus = item.focus
        return when {
            attachment == null || fresh == null -> upload.map { item }

            // still being converted when the wait ran out: worth another try later
            attachment.url == null -> Answer.Missed(ApiError.Server(PROCESSING, null))

            focus == null -> Answer.Got(fresh)

            else -> media.describe(account, attachment.id, item.description, focus).map { fresh }
        }
    }

    companion object {
        const val ACCOUNT = "account"

        /** What a server answers while it still converts an upload. */
        private const val PROCESSING = 206
        private const val MAX_ATTEMPTS = 8
    }
}

/** No answer, too many requests, or a server in trouble: the same post may go later. */
internal val ApiError.isWorthRetrying: Boolean
    get() = this is ApiError.Transport || this is ApiError.RateLimited || this is ApiError.Server
