// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.notifications

import social.aloha.core.data.previewText
import social.aloha.core.model.NotificationItem

/** The post a notification is about as a line of plain text; null when there is no post or nothing in it. */
public fun NotificationItem.preview(limit: Int = PREVIEW_LENGTH): String? = status?.displayed?.previewText(limit)

private const val PREVIEW_LENGTH = 280
