// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

/**
 * The notification an upload runs under: the file's name, how far it has got, and a way to cancel it.
 * Android asks for one while work runs in the foreground; it is silent and goes when the upload ends.
 */
internal class UploadNotifications(private val context: Context) {
    fun info(work: UUID, fileName: String, percent: Int?): ForegroundInfo {
        channel()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(context.getString(R.string.upload_title))
            .setContentText(fileName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(PERCENT, percent ?: 0, percent == null)
            .setStyle(
                NotificationCompat.ProgressStyle()
                    .addProgressSegment(NotificationCompat.ProgressStyle.Segment(PERCENT))
                    .setProgress(percent ?: 0)
                    .setProgressIndeterminate(percent == null),
            )
            .addAction(
                0,
                context.getString(R.string.upload_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(work),
            )
            .build()
        // one notification per upload, so two at once each show their own progress
        return foregroundInfo(work.hashCode(), notification)
    }

    private fun channel() = ensureChannel(
        context,
        Channel(
            CHANNEL,
            R.string.upload_channel,
            R.string.upload_channel_description,
            NotificationManager.IMPORTANCE_LOW,
        ),
    )

    private companion object {
        const val CHANNEL = "uploads"
        const val PERCENT = 100
    }
}
