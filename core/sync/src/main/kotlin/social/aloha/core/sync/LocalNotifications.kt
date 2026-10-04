// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import social.aloha.core.data.AppLockSettings
import social.aloha.core.data.notifications.preview
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.AppIntents

/** Loads an avatar for a notification; null when it cannot, and the notification goes without. */
public fun interface AvatarSource {
    public suspend fun load(url: String): Bitmap?
}

/** Whether the app may show notifications at all; asked for in the app, never from the background. */
public fun notificationsAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

/**
 * What the device shows of an account's notifications. Each is raised under the notification's key, so
 * a group that grows is updated in place, without alerting again; mentions read as a conversation with
 * their author, and one can be answered, favourited and muted or boosted from the notification once the
 * phone is unlocked. A private mention or a moderation notice shows only that it arrived on a locked screen. A
 * tap opens the post, or the profile of whoever did it, in the account it came to. With the digest on,
 * what it held back is raised as one summary instead, at the times chosen.
 */
public class LocalNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lock: AppLockSettings? = null,
    private val avatars: AvatarSource,
) {
    private val manager = NotificationManagerCompat.from(context)

    public val allowed: Boolean get() = notificationsAllowed(context)

    /** Raises [items] of [account]; [manyAccounts] adds the account's handle so it is clear whose they are. */
    internal suspend fun show(account: SignedInAccount, items: List<NotificationItem>, manyAccounts: Boolean) {
        if (!allowed || items.isEmpty()) return
        ensureChannels(context, account)
        items.forEach { item ->
            val builder = NotificationCompat.Builder(context, channelId(account.id, NoticeChannel.of(item.kind)))
                .setSmallIcon(R.drawable.ic_notification)
                .setWhen(item.latestAt.toEpochMilli())
                .setGroup(group(account.id))
                .setContentIntent(open(account.id, item))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            if (manyAccounts) builder.setSubText(account.qualifiedHandle)
            item.newest?.avatar?.let { avatars.load(it) }?.let(builder::setLargeIcon)
            if (item.kind == NotificationKind.Mention) conversation(builder, account, item) else plain(builder, item)
            if (item.answerable) actions(builder, account, item)
            if (item.private) {
                builder.setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE,
                ).setPublicVersion(locked(item))
            }
            post(tag(account.id), item.key.hashCode(), builder.build())
        }
        summary(account)
    }

    /**
     * Raises what the digest held back of [account] as one notification: how many, and of what kind,
     * opening the account's notifications. The one before it, if still up, is replaced.
     */
    internal fun showDigest(account: SignedInAccount, items: List<NotificationItem>, manyAccounts: Boolean) {
        if (!allowed || items.isEmpty()) return
        ensureChannels(context, account)
        val lines = DIGEST_LINES.mapNotNull { (channel, plural) ->
            val count = items.filter { NoticeChannel.of(it.kind) == channel }.sumOf { it.count }
            if (count > 0) context.resources.getQuantityString(plural, count, count) else null
        }
        val total = items.sumOf { it.count }
        val builder = NotificationCompat.Builder(context, channelId(account.id, NoticeChannel.Digest))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notification_summary, total, total))
            .setContentText(lines.joinToString(context.getString(R.string.notification_digest_separator)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setGroup(group(account.id))
            .setContentIntent(open(account.id, item = null))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        if (manyAccounts) builder.setSubText(account.qualifiedHandle)
        post(tag(account.id), DIGEST_ID, builder.build())
    }

    /** Takes [key]'s notification of [accountId] away, once it was acted on. */
    internal fun dismiss(accountId: String, key: String) {
        manager.cancel(tag(accountId), key.hashCode())
    }

    /** Forgets everything [accountId] had on the device: its notifications, its channels, its conversations. */
    public fun forget(accountId: String) {
        manager.activeNotifications.filter { it.tag == tag(accountId) }.forEach { manager.cancel(it.tag, it.id) }
        context.getSystemService<NotificationManager>()?.deleteNotificationChannelGroup(group(accountId))
        val conversations = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }
            .filter { it.startsWith(conversationPrefix(accountId)) }
        if (conversations.isEmpty()) return
        // the dynamic ones go from the launcher, the long-lived ones from the system's conversations
        ShortcutManagerCompat.removeDynamicShortcuts(context, conversations)
        ShortcutManagerCompat.removeLongLivedShortcuts(context, conversations)
    }

    private fun post(tag: String, id: Int, notification: Notification) {
        try {
            manager.notify(tag, id, notification)
        } catch (_: SecurityException) {
            // the permission was taken away since it was checked: nothing shows, and nothing breaks
        }
    }

    private fun plain(builder: NotificationCompat.Builder, item: NotificationItem) {
        val preview = preview(item)
        builder.setContentTitle(summaryOf(item)).setContentText(preview)
        preview?.let { builder.setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
    }

    /** What a locked screen shows of a private mention or a moderation notice: that one arrived. */
    private fun locked(item: NotificationItem): Notification = NotificationCompat.Builder(context, "")
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(
            context.getString(
                if (item.kind ==
                    NotificationKind.Mention
                ) {
                    R.string.notification_private
                } else {
                    R.string.notification_moderation
                },
            ),
        )
        .build()

    /**
     * A mention as a message from its author, in the Conversations part of the shade: a long-lived
     * shortcut per author and account carries it there.
     */
    private suspend fun conversation(
        builder: NotificationCompat.Builder,
        account: SignedInAccount,
        item: NotificationItem,
    ) {
        val author = item.newest ?: return plain(builder, item)
        val icon = author.avatar?.let { avatars.load(it) }?.let(IconCompat::createWithAdaptiveBitmap)
        val person = Person.Builder().setName(author.bestDisplayName).setKey(author.id).setIcon(icon).build()
        val me = Person.Builder().setName(context.getString(R.string.notification_you)).build()
        val shortcut = conversationPrefix(account.id) + author.id
        ShortcutManagerCompat.pushDynamicShortcut(
            context,
            ShortcutInfoCompat.Builder(context, shortcut)
                .setShortLabel(author.bestDisplayName.ifBlank { author.acct })
                .setPerson(person)
                .setLongLived(true)
                .setIcon(icon ?: IconCompat.createWithResource(context, R.drawable.ic_notification))
                .setIntent(AppIntents.open(context, account.id, item.status?.id, author.id))
                .build(),
        )
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(preview(item).orEmpty(), item.latestAt.toEpochMilli(), person)
            .setGroupConversation(false)
        builder.setStyle(style).setShortcutId(shortcut).setContentTitle(author.bestDisplayName)
        // a private mention is the one kind that is as personal as a message
        if (item.status?.visibility == Visibility.Direct) builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
    }

    private suspend fun actions(builder: NotificationCompat.Builder, account: SignedInAccount, item: NotificationItem) {
        // behind the app lock a tap opens the app, which asks first; a button would act without it
        if (lock?.enabled?.first() == true) return
        val reply = RemoteInput.Builder(NotificationActions.EXTRA_TEXT)
            .setLabel(context.getString(R.string.notification_reply_hint))
            .build()
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.notification_reply),
                action(account, item, NotificationActions.Kind.Reply),
            ).addRemoteInput(reply).setAllowGeneratedReplies(true).setAuthenticationRequired(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build(),
        )
        // three buttons fit: a mention's third mutes its conversation, since the shade is where a thread
        // that turned sour keeps coming back; a followed account's new post offers the boost instead
        val third = if (item.kind == NotificationKind.Mention) {
            NotificationActions.Kind.Mute to R.string.notification_mute
        } else {
            NotificationActions.Kind.Boost to R.string.notification_boost
        }
        listOf(NotificationActions.Kind.Favourite to R.string.notification_favourite, third).forEach { (kind, label) ->
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    context.getString(label),
                    action(account, item, kind),
                )
                    .setAuthenticationRequired(true)
                    .build(),
            )
        }
    }

    private fun action(account: SignedInAccount, item: NotificationItem, kind: NotificationActions.Kind) =
        PendingIntent.getBroadcast(
            context,
            0,
            NotificationActions.intent(context, account, item, kind),
            // a reply's text is filled in by the system, which needs the intent mutable
            (
                if (kind ==
                    NotificationActions.Kind.Reply
                ) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_IMMUTABLE
                }
                ) or
                PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** What a tap opens: [item]'s post or author, or the account's notifications for a digest. */
    private fun open(accountId: String, item: NotificationItem?): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        AppIntents.open(context, accountId, item?.status?.id, item?.newest?.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun summary(account: SignedInAccount) {
        val count = manager.activeNotifications.count { it.tag == tag(account.id) && !it.isGroupSummary() }
        val text = context.resources.getQuantityString(R.plurals.notification_summary, count, count)
        val summary = NotificationCompat.Builder(context, channelId(account.id, NoticeChannel.Mentions))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setSubText(account.qualifiedHandle)
            .setGroup(group(account.id))
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .build()
        post(tag(account.id), SUMMARY_ID, summary)
    }

    private fun summaryOf(item: NotificationItem): String =
        NotificationText.summary(context.resources, item.kind, item.newest?.bestDisplayName, item.others)

    private fun preview(item: NotificationItem): String? = item.preview(PREVIEW)

    public companion object {
        private const val SUMMARY_ID = 0
        private const val DIGEST_ID = 1
        private const val PREVIEW = 500

        // the order a digest names its parts in: what asks for an answer first, what merely happened last
        private val DIGEST_LINES = listOf(
            NoticeChannel.Mentions to R.plurals.notification_digest_mentions,
            NoticeChannel.Posts to R.plurals.notification_digest_posts,
            NoticeChannel.Follows to R.plurals.notification_digest_follows,
            NoticeChannel.Boosts to R.plurals.notification_digest_boosts,
            NoticeChannel.Favourites to R.plurals.notification_digest_favourites,
            NoticeChannel.Polls to R.plurals.notification_digest_polls,
            NoticeChannel.Edits to R.plurals.notification_digest_edits,
            NoticeChannel.Moderation to R.plurals.notification_digest_notices,
        )

        private fun tag(accountId: String) = "notifications:$accountId"

        private fun conversationPrefix(accountId: String) = "conversation:$accountId:"
    }
}

/** Someone else's post to answer, favourite or boost: a mention, or a post of someone followed. */
private val NotificationItem.answerable: Boolean
    get() = status != null && (kind == NotificationKind.Mention || kind == NotificationKind.Status)

/** What a locked screen must not show: a private mention, or what the moderators decided. */
private val NotificationItem.private: Boolean
    get() = (kind == NotificationKind.Mention && status?.visibility == Visibility.Direct) ||
        NoticeChannel.of(kind) == NoticeChannel.Moderation
