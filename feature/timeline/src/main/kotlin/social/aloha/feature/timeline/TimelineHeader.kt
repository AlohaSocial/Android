// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import social.aloha.core.ui.rememberReducedMotion

/**
 * What another feature puts atop the timeline, such as Photos' stories: shown while the posts are read from
 * the top, it slides away once they are scrolled and back when they reach the top again.
 */
@Composable
internal fun TimelineHeader(
    state: TimelineUiState,
    listState: LazyListState,
    gridState: LazyGridState,
    header: @Composable () -> Unit,
) {
    val grid = state.items.isNotEmpty() && (state.grid == true || state.watched != null)
    val scrolled = if (grid) gridState.canScrollBackward else listState.canScrollBackward
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = !scrolled,
        enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
        exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut(),
    ) {
        header()
    }
}
