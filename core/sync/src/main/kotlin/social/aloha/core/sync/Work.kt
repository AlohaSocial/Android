// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.NetworkType

/** Work that talks to a server waits for a network. */
internal val NEEDS_NETWORK: Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** The quiet notice Android below 12 shows while a push is being looked at. */
internal fun checking(context: Context): Notification {
    ensureChannel(
        context,
        Channel(CHECKING, R.string.sync_channel, R.string.sync_channel_description, NotificationManager.IMPORTANCE_MIN),
    )
    return NotificationCompat.Builder(context, CHECKING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.sync_checking))
        .setSilent(true)
        .build()
}

private const val CHECKING = "checking"
