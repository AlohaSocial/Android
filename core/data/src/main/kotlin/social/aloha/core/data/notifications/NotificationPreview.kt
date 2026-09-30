// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.notifications

import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.NotificationItem

/**
 * The post a notification is about as a line of plain text: its content warning when it has one, never
 * the text behind it, else its text. Null when there is no post or nothing in it.
 */
public fun NotificationItem.preview(limit: Int = PREVIEW_LENGTH): String? = status?.displayed?.let {
    it.spoilerText.ifBlank { StatusHtmlParser.plainText(it.content) }
}?.take(limit)?.ifBlank { null }

private const val PREVIEW_LENGTH = 280
