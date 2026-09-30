// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import java.util.UUID

/**
 * What the outbox tells the writer: that posts are going out, while they do, and that one needs them,
 * refused by the server or waiting for a new sign-in. A tap opens the app on it.
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
