// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.VideoCard

/**
 * Video as cards: one across on a phone, as many as fit from 320 dp wide on larger screens, each with
 * how far the reader got. A tap opens the video's watch page.
 */
@Composable
internal fun VideoGrid(
    state: TimelineUiState,
    actions: TimelineScreenActions,
    onVideo: (String) -> Unit,
    gridState: LazyGridState,
) {
    val watched = state.watched.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD),
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag(VIDEO_GRID),
        contentPadding = PaddingValues(start = AlohaSpacing.s, end = AlohaSpacing.s, bottom = AlohaSpacing.xl),
    ) {
        items(
            state.items,
            key = { it.key },
            contentType = { it::class },
            span = { if (it is TimelineItem.Gap) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { item ->
            when (item) {
                is TimelineItem.Post -> VideoCard(item.row, watched[item.row.statusId], onOpen = {
                    onVideo(item.row.statusId)
                })

                is TimelineItem.Gap -> GapRow(item, actions)
            }
        }
        if (state.loadingOlder) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { ListProgress() }
        }
    }
    GridEffects(state, actions, gridState)
}

private val CARD = 320.dp

/** The video grid's test tag. */
internal const val VIDEO_GRID = "video-grid"
