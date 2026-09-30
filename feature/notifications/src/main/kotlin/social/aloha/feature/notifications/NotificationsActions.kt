// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import social.aloha.core.model.NotificationKind

/** What the screen asks of whoever shows it. */
internal interface NotificationsActions {
    fun onRefresh()

    fun onKind(kind: NotificationKind)

    fun onAllKinds()

    fun onNearEnd()

    fun onOpen(row: NotificationRowUi)

    fun onOthers(groupKey: String)

    fun onPolicy()

    fun onRequests()

    fun onAskedForPermission()
}
