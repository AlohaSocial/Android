// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.DraftSegment
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility

/** What a notification's buttons do, and what their intents carry to do it. */
internal object NotificationActions {
    enum class Kind { Reply, Favourite, Boost, Mute }

    const val EXTRA_TEXT = "reply"
    const val ACCOUNT = "account"
    const val KEY = "key"
    const val STATUS = "status"
    const val KIND = "kind"
    const val MENTIONS = "mentions"
    const val VISIBILITY = "visibility"
    const val SPOILER = "spoiler"
    const val LANGUAGE = "language"

    /**
     * The intent of [kind] on [item] of [account], told apart from every other by its data. A reply's
     * carries whom it addresses and how private it must be, so it can go to the outbox without asking.
     */
    fun intent(context: Context, account: SignedInAccount, item: NotificationItem, kind: Kind): Intent {
        val status = item.status
        val shown = status?.displayed
        return Intent(context, NotificationActionReceiver::class.java)
            .setAction("social.aloha.action.NOTIFICATION_${kind.name.uppercase()}")
            .setData("aloha-notification:${account.id}/${item.key}/${kind.name}".toUri())
            .putExtra(ACCOUNT, account.id)
            .putExtra(KEY, item.key)
            .putExtra(STATUS, shown?.id)
            .putExtra(KIND, kind.name)
            .apply {
                if (kind == Kind.Reply && status != null && shown != null) {
                    putExtra(MENTIONS, status.replyMentions(account.handle.removePrefix("@")).toTypedArray())
                    // no less private than what it answers
                    putExtra(VISIBILITY, status.replyVisibility(Visibility.Public).wire)
                    putExtra(SPOILER, shown.spoilerText.ifBlank { null })
                    putExtra(LANGUAGE, shown.language)
                }
            }
    }
}

/**
 * A tap on a notification's button. An answer goes straight into the outbox, which sends it when it can
 * and says so if the server refuses it; it never waits in WorkManager's store, which backups reach. A
 * favourite or boost is handed to WorkManager, and waits for a network if there is none.
 */
@AndroidEntryPoint
public class NotificationActionReceiver : BroadcastReceiver() {
    @Inject internal lateinit var queue: PostQueue

    @Inject internal lateinit var notifications: LocalNotifications

    @Inject
    @ApplicationScope
    internal lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(NotificationActions.ACCOUNT)
        val key = intent.getStringExtra(NotificationActions.KEY)
        val statusId = intent.getStringExtra(NotificationActions.STATUS)
        if (accountId == null || key == null || statusId == null) return
        val kind = NotificationActions.Kind.entries.firstOrNull {
            it.name ==
                intent.getStringExtra(NotificationActions.KIND)
        }
        if (kind == NotificationActions.Kind.Reply) answer(intent, accountId, key, statusId) else react(context, intent)
    }

    private fun answer(intent: Intent, accountId: String, key: String, statusId: String) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationActions.EXTRA_TEXT)
            ?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                queue.queue(UUID.randomUUID().toString(), accountId, reply(intent, statusId, text))
                notifications.dismiss(accountId, key)
            } finally {
                pending.finish()
            }
        }
    }

    private fun react(context: Context, intent: Intent) {
        val work = OneTimeWorkRequestBuilder<NotificationActionWorker>()
            .setInputData(
                workDataOf(
                    NotificationActions.ACCOUNT to intent.getStringExtra(NotificationActions.ACCOUNT),
                    NotificationActions.KEY to intent.getStringExtra(NotificationActions.KEY),
                    NotificationActions.STATUS to intent.getStringExtra(NotificationActions.STATUS),
                    NotificationActions.KIND to intent.getStringExtra(NotificationActions.KIND),
                ),
            )
            .setConstraints(NEEDS_NETWORK)
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    private fun reply(intent: Intent, statusId: String, text: String): DraftPost {
        val mentions = intent.getStringArrayExtra(NotificationActions.MENTIONS).orEmpty()
            .map { "@$it" }
            .filterNot { text.contains(it, ignoreCase = true) }
        return DraftPost(
            segments = listOf(DraftSegment((mentions + text).joinToString(" "))),
            replyToId = statusId,
            spoiler = intent.getStringExtra(NotificationActions.SPOILER),
            visibility = Visibility.fromWire(intent.getStringExtra(NotificationActions.VISIBILITY)),
            language = intent.getStringExtra(NotificationActions.LANGUAGE),
        )
    }
}

/**
 * Favourites, boosts or mutes the post a notification is about, as the account it came to, then takes
 * the notification away. A favourite, boost or mute that is already there stays.
 */
@HiltWorker
internal class NotificationActionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val compose: ComposeRepository,
    private val interactions: StatusInteractions,
    private val notifications: LocalNotifications,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(NotificationActions.ACCOUNT)?.let { accounts.byId(it) }
        val kind = inputData.getString(NotificationActions.KIND)
            ?.let { name -> NotificationActions.Kind.entries.firstOrNull { it.name == name } }
        val statusId = inputData.getString(NotificationActions.STATUS).orEmpty()
        if (account == null || kind == null || statusId.isEmpty()) return Result.success()
        return when (val found = compose.status(account, statusId)) {
            is Answer.Got -> {
                act(account, kind, found.value)
                Result.success()
            }

            // a tap is not worth trying forever: after a few attempts it is let go
            is Answer.Missed ->
                if (found.error.isWorthRetrying && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    private suspend fun act(account: SignedInAccount, kind: NotificationActions.Kind, status: Status) {
        kind.toggleFor(status)?.let { interactions.toggle(account, status, it) }
        inputData.getString(NotificationActions.KEY)?.let { notifications.dismiss(account.id, it) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/** What this tap turns on for [status]; nothing where that is already done, on another device or in the app. */
private fun NotificationActions.Kind.toggleFor(status: Status): Toggle? {
    val shown = status.displayed
    return when (this) {
        NotificationActions.Kind.Favourite -> Toggle.Favourite.takeUnless { shown.favourited }
        NotificationActions.Kind.Boost -> Toggle.Boost.takeUnless { shown.reblogged }
        NotificationActions.Kind.Mute -> Toggle.MuteConversation.takeUnless { shown.muted }
        NotificationActions.Kind.Reply -> null
    }
}
