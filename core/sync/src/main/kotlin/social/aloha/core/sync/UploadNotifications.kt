// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import java.util.UUID

/**
 * The notification an upload runs under: the file's name and how far it has got. Android asks for
 * one while work runs in the foreground; it is silent and goes when the upload ends.
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
