// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.CaughtUpDivider
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.MediaImage
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.rememberBlurHashPainter

/**
 * Photos as a grid: each post one square of its first picture, three across on a phone and more as the
 * screen widens, a stack badge where it holds more. A sensitive picture shows as its blurhash until it
 * is opened. A tap opens the pictures, and each square reads as one button saying whose it is.
 */
@Composable
internal fun PhotoGrid(
    state: TimelineUiState,
    actions: TimelineScreenActions,
    rowActions: StatusActions,
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CELL),
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag(PHOTO_GRID),
        contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
    ) {
        items(
            state.items,
            key = { it.key },
            contentType = { it::class },
            span = { if (it is TimelineItem.Post) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
        ) { item ->
            when (item) {
                is TimelineItem.Post -> Cell(item.row, onOpen = { rowActions.onMedia(item.row, 0) })
                is TimelineItem.Gap -> GapRow(item, actions)
                TimelineItem.CaughtUp -> CaughtUpDivider(onClick = actions::onCaughtUp)
            }
        }
        if (state.loadingOlder) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { ListProgress() }
        }
    }
    GridEffects(state, actions, gridState)
}

@Composable
private fun Cell(row: StatusRowUi, onOpen: () -> Unit) {
    val first = row.media.firstOrNull() ?: return
    val alt = first.description?.takeIf { it.isNotBlank() }
    val count = row.media.size
    val label = listOfNotNull(
        pluralStringResource(R.plurals.photos_cell, count, row.author.plainName, count),
        alt ?: stringResource(R.string.photos_cell_no_alt),
        stringResource(R.string.photos_cell_sensitive).takeIf { row.sensitive },
    ).joinToString(", ")
    val open = stringResource(R.string.photos_cell_open)
    Box(
        Modifier.aspectRatio(1f).padding(CELL_GAP).clickable(onClick = onOpen).clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick(open) {
                onOpen()
                true
            }
        },
    ) {
        if (row.sensitive) {
            // what a sensitive picture shows is its blur until it is opened
            rememberBlurHashPainter(first.blurhash)?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Icon(
                AlohaIcons.Sensitive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            MediaImage(first, contentDescription = null, modifier = Modifier.fillMaxSize(), fitToAspect = false)
        }
        if (count > 1) {
            // on a scrim of its own, so it reads on a light picture as on a dark one
            Icon(
                AlohaIcons.Stack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(AlohaSpacing.xs)
                    .background(Color.Black.copy(alpha = SCRIM), CircleShape).padding(BADGE_PADDING).size(BADGE),
            )
        }
    }
}

/**
 * Photos' own toolbar buttons, each where it applies: Explore, the reader's albums, and the switch
 * between grid and feed ([grid] is null for a timeline that is always a list).
 */
@Composable
internal fun PhotosButtons(
    grid: Boolean?,
    onGrid: (Boolean) -> Unit,
    onAlbums: (() -> Unit)?,
    onExplore: (() -> Unit)?,
) {
    onExplore?.let {
        IconButton(onClick = it) { Icon(AlohaIcons.Explore, stringResource(R.string.photos_explore)) }
    }
    onAlbums?.let {
        IconButton(onClick = it) { Icon(AlohaIcons.Album, stringResource(R.string.photos_albums)) }
    }
    grid?.let { LayoutToggle(it, onGrid) }
}

/** Switches Photos between its grid and its feed, naming what a tap switches to. */
@Composable
private fun LayoutToggle(grid: Boolean, onGrid: (Boolean) -> Unit) {
    IconButton(onClick = { onGrid(!grid) }) {
        if (grid) {
            Icon(AlohaIcons.Feed, stringResource(R.string.photos_show_feed))
        } else {
            Icon(AlohaIcons.Grid, stringResource(R.string.photos_show_grid))
        }
    }
}

/** The grid's side of what the list does: its position kept, the next page near the end, the scroll asked for. */
@Composable
internal fun GridEffects(state: TimelineUiState, actions: TimelineScreenActions, gridState: LazyGridState) {
    val items by rememberUpdatedState(state.items)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                (items.getOrNull(index) as? TimelineItem.Post)?.let { actions.onScrolled(it.key, offset) }
            }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { last -> items.isNotEmpty() && last >= items.size - NEAR_END }
            .collect { actions.onNearEnd() }
    }
    LaunchedEffect(state.restoreTo) {
        state.restoreTo?.let {
            gridState.scrollToItem(it.index, it.offset)
            actions.onRestored()
        }
    }
    LaunchedEffect(state.scrollToTop) {
        if (state.scrollToTop) {
            gridState.animateScrollToItem(0)
            actions.onScrolledToTop()
        }
    }
}

private val CELL = 112.dp
private val CELL_GAP = 1.dp
private val BADGE = 16.dp
private val BADGE_PADDING = 3.dp
private const val SCRIM = 0.5f

// a grid shows three rows' worth where a list shows one, so the next page starts sooner
private const val NEAR_END = 30

/** The photo grid's test tag. */
internal const val PHOTO_GRID = "photo-grid"
