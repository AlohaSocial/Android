// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

/**
 * The notifications of an export: one while it runs, which can cancel it, and one once it is saved or
 * has failed, since the reader may have left the page long before.
 */
internal class ExportNotifications(private val context: Context) {
    fun running(work: UUID): ForegroundInfo {
        channel()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.export_running))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .addAction(
                0,
                context.getString(R.string.export_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(work),
            )
            .build()
        return foregroundInfo(work.hashCode(), notification)
    }

    @SuppressLint("MissingPermission") // notificationsAllowed checks it
    fun finished(work: UUID, saved: Boolean) {
        if (!notificationsAllowed(context)) return
        channel()
        val title = if (saved) R.string.export_notification_saved else R.string.export_notification_failed
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(title))
            .setAutoCancel(true)
            .build()
        // not the running one's id: WorkManager takes that one away when the work ends
        NotificationManagerCompat.from(context).notify(work.hashCode() + 1, notification)
    }

    private fun channel() = ensureChannel(
        context,
        Channel(
            CHANNEL,
            R.string.export_channel,
            R.string.export_channel_description,
            NotificationManager.IMPORTANCE_LOW,
        ),
    )

    private companion object {
        const val CHANNEL = "exports"
    }
}
