// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The widest a column of posts reads well at; on a wide window the column centres rather than stretching. */
public val ReadingWidth: Dp = 840.dp

public fun Modifier.readingWidth(): Modifier = widthIn(max = ReadingWidth)
