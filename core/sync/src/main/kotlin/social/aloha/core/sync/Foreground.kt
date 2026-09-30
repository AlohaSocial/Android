// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo

/**
 * Runs the rest of this work in the foreground, so a long upload finishes with the app gone. Started
 * from the background, where Android refuses a foreground service, the work goes on as it is.
 */
internal suspend fun CoroutineWorker.tryForeground() {
    try {
        setForeground(getForegroundInfo())
    } catch (_: IllegalStateException) {
        // refused: the work still runs, only without its notification
    }
}

/** [notification] as the foreground notification [id] of data-syncing work. */
internal fun foregroundInfo(id: Int, notification: Notification): ForegroundInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(id, notification)
    }

/** A notification channel: its [id], and the [name] and [description] the system settings show. */
internal data class Channel(val id: String, val name: Int, val description: Int, val importance: Int)

/** Makes [channel] once. */
internal fun ensureChannel(context: Context, channel: Channel) {
    val manager = context.getSystemService<NotificationManager>() ?: return
    if (manager.getNotificationChannel(channel.id) != null) return
    val made = NotificationChannel(channel.id, context.getString(channel.name), channel.importance)
    made.description = context.getString(channel.description)
    manager.createNotificationChannel(made)
}
