// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AppIntents
import social.aloha.core.sync.AvatarSource

/**
 * The launcher's shortcuts: New post, Search and Notifications, which act as the account in use, then
 * one per signed-in account. An account's shortcut in the share sheet shares straight into its composer
 * (Direct Share, through the share target in `shortcuts.xml`); with more than one account the launcher
 * offers it too, as "Post as". All are dynamic: the system starts a static shortcut with
 * `FLAG_ACTIVITY_CLEAR_TASK`, which would close whatever was open. The conversation shortcuts
 * notifications publish are left alone.
 */
class AccountShortcuts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val avatars: AvatarSource,
) {
    suspend fun publish(accounts: List<SignedInAccount>) {
        val wanted = accounts.map { idOf(it.id) }.toSet()
        val gone = ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED,
        ).map { it.id }.filter { it.startsWith(PREFIX) && it !in wanted }
        if (gone.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, gone)
            ShortcutManagerCompat.removeLongLivedShortcuts(context, gone)
        }
        APP.forEachIndexed { rank, (id, action) ->
            val (label, long, icon) = appShortcut.getValue(id)
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(context.getString(label))
                .setLongLabel(context.getString(long))
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(AppIntents.asActiveAccount(context, action))
                .setRank(rank)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
        accounts.forEachIndexed { index, account ->
            val icon = account.avatarUrl?.let { avatars.load(it) }?.let(IconCompat::createWithAdaptiveBitmap)
                ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
            val shortcut = ShortcutInfoCompat.Builder(context, idOf(account.id))
                .setShortLabel(account.qualifiedHandle)
                .setLongLabel(context.getString(R.string.shortcut_post_as, account.qualifiedHandle))
                .setIcon(icon)
                .setLongLived(true)
                .setCategories(setOf(SHARE_CATEGORY))
                .setIntent(AppIntents.compose(context, account.id))
                // after the app's three, in the order the reader arranged the accounts
                .setRank(APP.size + index)
            // never excluded from the launcher, though with one account New post already posts as it:
            // the system drops a dynamic shortcut excluded from it, which takes it out of the share sheet
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut.build())
        }
    }

    companion object {
        private const val PREFIX = "account:"

        private val APP = listOf(
            "compose" to AppIntents.ACTION_COMPOSE,
            "search" to AppIntents.ACTION_SEARCH,
            "notifications" to AppIntents.ACTION_OPEN,
        )
        private val appShortcut = mapOf(
            "compose" to Triple(
                R.string.shortcut_compose,
                R.string.shortcut_compose_long,
                R.drawable.ic_shortcut_compose,
            ),
            "search" to Triple(R.string.shortcut_search, R.string.shortcut_search, R.drawable.ic_shortcut_search),
            "notifications" to Triple(
                R.string.destination_notifications,
                R.string.destination_notifications,
                R.drawable.ic_shortcut_notifications,
            ),
        )

        /** Matches the share target's category in `shortcuts.xml`. */
        private const val SHARE_CATEGORY = "social.aloha.category.SHARE_TARGET"

        private fun idOf(accountId: String) = PREFIX + accountId

        /** The account a Direct Share shortcut names, or null for any other shortcut. */
        fun accountOf(shortcutId: String?): String? = shortcutId?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
    }
}
