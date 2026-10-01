// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import social.aloha.core.navigation.AppIntents
import social.aloha.core.sync.PostQueue

/**
 * What an intent from outside the app's screens asks for: a link, something shared, a draft, a post or
 * profile from a notification, a new post or search from a shortcut. An account it names is only a
 * request: the app looks it up, never trusts it as given. Without one, it is the account in use.
 */
sealed interface OutsideRequest {
    data class Link(val address: String) : OutsideRequest

    data class Share(val content: SharedContent, val accountId: String?) : OutsideRequest

    data class Draft(val accountId: String, val draftId: String?) : OutsideRequest

    data class Open(val accountId: String?, val statusId: String?, val profileId: String?) : OutsideRequest

    data class Compose(val accountId: String?) : OutsideRequest

    data class Search(val accountId: String?) : OutsideRequest

    companion object {
        /** What [intent] asks for, or null for nothing the app answers; [ownPackage] keeps its files out. */
        fun of(intent: Intent, ownPackage: String): OutsideRequest? {
            val account = intent.getStringExtra(AppIntents.EXTRA_ACCOUNT)
            return when (intent.action) {
                Intent.ACTION_VIEW -> intent.dataString?.let { Link(it) }

                PostQueue.ACTION_OPEN_DRAFT -> intent.getStringExtra(PostQueue.EXTRA_ACCOUNT)?.let {
                    Draft(it, intent.getStringExtra(PostQueue.EXTRA_DRAFT))
                }

                // a Direct Share target names the account to post as
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> SharedContent.from(intent, ownPackage)?.let {
                    val shortcut = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
                    Share(it, AccountShortcuts.accountOf(shortcut))
                }

                AppIntents.ACTION_OPEN -> Open(
                    account,
                    intent.getStringExtra(AppIntents.EXTRA_STATUS),
                    intent.getStringExtra(AppIntents.EXTRA_PROFILE),
                )

                AppIntents.ACTION_COMPOSE -> Compose(account)

                AppIntents.ACTION_SEARCH -> Search(account)

                else -> null
            }
        }
    }
}
