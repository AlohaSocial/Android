// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import java.time.Duration
import social.aloha.core.model.NotificationKind

/** What a notification's row asks for. */
internal interface NotificationRowActions {
    fun onOpen(row: NotificationRowUi)

    /** Everyone in the group of row [key], which may be another account's than the one in use. */
    fun onOthers(key: String)

    /** No more notifications from the conversation a mention or reply belongs to. */
    fun onMuteConversation(row: NotificationRowUi)

    fun onProfile(accountId: String)

    fun onFollowRequest(row: NotificationRowUi, accept: Boolean)

    fun onLearnMore(url: String)
}

/** What the screen asks of whoever shows it, its rows' asks among them. */
internal interface NotificationsActions : NotificationRowActions {
    fun onRefresh()

    fun onKind(kind: NotificationKind)

    fun onAllKinds()

    fun onNearEnd()

    fun onNoticeShown()

    fun onPolicy()

    fun onRequests()

    fun onAskedForPermission()

    fun onMarkAllRead()

    /** Holds every notification back for [length]; null has them raised again at once. */
    fun onPause(length: Duration?)
}
