// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.Outbox

/**
 * Sends each account's queued posts, oldest first, once there is a network: one unique chain of work
 * per account, so two posts never race each other and a post queued while the queue drains goes out
 * after it.
 */
@Singleton
public class PostQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outbox: Outbox,
) {
    /** Queues [post] as [id] of [accountId] and drains the queue it joined. */
    public suspend fun queue(id: String, accountId: String, post: DraftPost) {
        outbox.queue(id, accountId, post)
        drain(accountId)
    }

    /** Lets [accountId]'s paused posts go out again, and drains its queue. */
    public suspend fun resume(accountId: String) {
        outbox.setPaused(accountId, paused = false)
        drain(accountId)
    }

    /** Drains [accountId]'s queue: now when there is a network, else as soon as there is one. */
    public fun drain(accountId: String) {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setInputData(workDataOf(OutboxWorker.ACCOUNT to accountId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            // straight away when the system allows it, as ordinary work when the quota is spent
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // appended, a drain asked for while one runs follows it and finds what was queued meanwhile
        WorkManager.getInstance(context)
            .enqueueUniqueWork("$NAME$accountId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    public companion object {
        /** The account whose draft a notification opens. */
        public const val EXTRA_ACCOUNT: String = "social.aloha.extra.OUTBOX_ACCOUNT"

        /** The draft a notification opens in the composer; none opens the app. */
        public const val EXTRA_DRAFT: String = "social.aloha.extra.OUTBOX_DRAFT"

        /** What a notification's tap sends to the app, which checks both against its own drafts. */
        public const val ACTION_OPEN_DRAFT: String = "social.aloha.action.OPEN_DRAFT"

        private const val NAME = "outbox-"
        private val BACKOFF: Duration = Duration.ofSeconds(30)

        internal fun openDraft(context: Context, accountId: String, draftId: String?): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            val intent = launch.setAction(ACTION_OPEN_DRAFT)
                .putExtra(EXTRA_ACCOUNT, accountId)
                .putExtra(EXTRA_DRAFT, draftId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                (draftId ?: accountId).hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
