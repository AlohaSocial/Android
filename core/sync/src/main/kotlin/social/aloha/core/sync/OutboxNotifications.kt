// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import java.util.UUID
import social.aloha.core.data.compose.OutboxEntry

/**
 * What the outbox tells the writer: that posts are going out, while they do, that one needs them,
 * refused by the server or waiting for a new sign-in, and that one waits for a moderator. A tap opens
 * the app on it.
 */
internal class OutboxNotifications(private val context: Context) {
    fun sending(work: UUID): ForegroundInfo {
        channel()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(context.getString(R.string.outbox_sending))
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
        return foregroundInfo(work.hashCode(), notification)
    }

    /** Post [draftId] of [accountId] was refused, [message] saying why; a tap opens it in the composer. */
    fun refused(accountId: String, draftId: String, message: String?) = show(
        draftId.hashCode(),
        context.getString(R.string.outbox_refused),
        message ?: context.getString(R.string.outbox_refused_unknown),
        PostQueue.openDraft(context, accountId, draftId),
    )

    /**
     * Post [entry] of [accountId] is held for a moderator to look at, by the rule [reason] names, and
     * the [kept] segments of its thread after it are draft [entry] now; a tap opens that draft, or the
     * app when none was kept. One written again says its original stays up.
     */
    fun held(accountId: String, entry: OutboxEntry, kept: Int, reason: String?) {
        val held = if (kept > 0) {
            context.resources.getQuantityString(R.plurals.outbox_held_thread, kept, kept)
        } else {
            context.getString(R.string.outbox_held_body)
        }
        show(
            entry.id.hashCode(),
            context.getString(R.string.outbox_held),
            heldMessage(context.resources, held, reason, replaces = entry.post.replaces != null),
            PostQueue.openDraft(context, accountId, entry.id.takeIf { kept > 0 }),
        )
    }

    /** [handle]'s queue waits for a new sign-in. */
    fun paused(accountId: String, handle: String) = show(
        accountId.hashCode(),
        context.getString(R.string.outbox_paused),
        context.getString(R.string.outbox_paused_body, handle),
        PostQueue.openDraft(context, accountId, draftId = null),
    )

    private fun show(id: Int, title: String, text: String, open: PendingIntent?) {
        // ponytail: without the permission nothing shows; the drafts list still says what happened
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        channel()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(TAG, id, notification)
    }

    private fun channel() = ensureChannel(
        context,
        Channel(
            CHANNEL,
            R.string.outbox_channel,
            R.string.outbox_channel_description,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )

    private companion object {
        const val CHANNEL = "outbox"
        const val TAG = "outbox"
    }
}

/**
 * [message], that a post waits for a moderator, followed by that the original stays up for the writer
 * to delete when the post [replaces] one, and by the sentence that explains review rule [reason] as Social names them
 * (`first_post`, `links`, `mentions` and `repeat` from 0.20.x, `video` from 0.21.0, read from source);
 * no sentence for a rule it does not know.
 */
public fun heldMessage(words: Resources, message: String, reason: String?, replaces: Boolean): String {
    val told = if (replaces) words.getString(R.string.held_keeps_original, message) else message
    val why = when (reason) {
        "first_post" -> R.string.held_reason_first_post
        "links" -> R.string.held_reason_links
        "mentions" -> R.string.held_reason_mentions
        "repeat" -> R.string.held_reason_repeat
        "video" -> R.string.held_reason_video
        else -> return told
    }
    return words.getString(R.string.held_with_reason, told, words.getString(why))
}
