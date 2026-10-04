// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The hairline between one post and the next, edge to edge; none where Reading turned the lines off. */
@Composable
public fun PostDivider(modifier: Modifier = Modifier) {
    if (!LocalReadingStyle.current.postLines) return
    HorizontalDivider(modifier, thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
