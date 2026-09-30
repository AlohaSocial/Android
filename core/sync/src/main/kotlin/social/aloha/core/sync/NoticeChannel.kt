// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.content.getSystemService
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.SignedInAccount

/** The channels each account's notifications go to; each is its own switch in the system settings. */
internal enum class NoticeChannel(val suffix: String, val title: Int, val importance: Int) {
    Mentions("mentions", R.string.notification_channel_mentions, NotificationManager.IMPORTANCE_HIGH),
    Follows("follows", R.string.notification_channel_follows, NotificationManager.IMPORTANCE_DEFAULT),
    Favourites("favourites", R.string.notification_channel_favourites, NotificationManager.IMPORTANCE_LOW),
    Boosts("boosts", R.string.notification_channel_boosts, NotificationManager.IMPORTANCE_LOW),
    Polls("polls", R.string.notification_channel_polls, NotificationManager.IMPORTANCE_DEFAULT),
    Posts("posts", R.string.notification_channel_posts, NotificationManager.IMPORTANCE_DEFAULT),
    Edits("edits", R.string.notification_channel_edits, NotificationManager.IMPORTANCE_LOW),
    Moderation("moderation", R.string.notification_channel_moderation, NotificationManager.IMPORTANCE_HIGH),
    ;

    companion object {
        fun of(kind: NotificationKind): NoticeChannel = when (kind) {
            NotificationKind.Mention -> Mentions
            NotificationKind.Follow, NotificationKind.FollowRequest -> Follows
            NotificationKind.Favourite -> Favourites
            NotificationKind.Reblog -> Boosts
            NotificationKind.Poll -> Polls
            NotificationKind.Status -> Posts
            NotificationKind.Update -> Edits
            else -> Moderation
        }
    }
}

/** The channel group of [accountId]: one per account, named by its handle. */
internal fun group(accountId: String) = "account:$accountId"

internal fun channelId(accountId: String, channel: NoticeChannel) = "$accountId:${channel.suffix}"

/** Makes [account]'s channel group and each of its channels once. */
internal fun ensureChannels(context: Context, account: SignedInAccount) {
    val system = context.getSystemService<NotificationManager>() ?: return
    system.createNotificationChannelGroup(
        NotificationChannelGroup(
            group(account.id),
            context.getString(R.string.notification_group, account.qualifiedHandle),
        ),
    )
    NoticeChannel.entries.forEach { channel ->
        if (system.getNotificationChannel(channelId(account.id, channel)) == null) {
            system.createNotificationChannel(
                NotificationChannel(
                    channelId(account.id, channel),
                    context.getString(channel.title),
                    channel.importance,
                )
                    .apply { group = group(account.id) },
            )
        }
    }
}

internal fun StatusBarNotification.isGroupSummary() = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
