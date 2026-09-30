// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Status

/**
 * A post as one line of plain text, where no rich text is drawn: its content warning when it has one,
 * never the text behind it, else its text. Null when there is nothing in it.
 */
public fun Status.previewText(limit: Int = PREVIEW_LENGTH): String? =
    spoilerText.ifBlank { StatusHtmlParser.plainText(content) }.take(limit).ifBlank { null }

private const val PREVIEW_LENGTH = 280
