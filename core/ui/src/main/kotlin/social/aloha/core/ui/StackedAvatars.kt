// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A few people as overlapping avatars, the first on top, each ringed in the surface colour so the overlap
 * reads. Decoration only: the text beside it names them.
 */
@Composable
public fun StackedAvatars(urls: List<String?>, size: Dp, modifier: Modifier = Modifier, maximum: Int = MAXIMUM) {
    val shown = urls.take(maximum)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-size / OVERLAP)) {
        shown.forEachIndexed { index, url ->
            Avatar(
                url,
                size,
                Modifier
                    .zIndex((shown.size - index).toFloat())
                    .border(RING, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

private const val MAXIMUM = 4
private const val OVERLAP = 3
private val RING = 2.dp
