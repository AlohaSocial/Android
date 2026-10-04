// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaSpacing

/**
 * Tabs over pages: a tap on a tab or a swipe across the pages moves between them, the indicator going
 * with the page, and the row stays where it is while a page scrolls under it. [selected] is the
 * caller's; a swipe that settles on another page tells it through [onSelect], and a tap does the same,
 * the pages following. Each page keeps its own scroll position while the reader is elsewhere.
 * [scrollable] rows size each tab to its label, so a large font scrolls the row instead of breaking a word.
 */
@Composable
public fun <T> TabPager(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    edgePadding: Dp = AlohaSpacing.m,
    page: @Composable (T) -> Unit,
) {
    val index = tabs.indexOf(selected).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = index) { tabs.size }
    val shown by rememberUpdatedState(tabs)
    val chosen by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(index) { if (pager.currentPage != index) pager.animateScrollToPage(index) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { settled ->
            shown.getOrNull(settled)?.takeIf { it != chosen }?.let(select)
        }
    }
    Column(modifier) {
        val row: @Composable () -> Unit = {
            tabs.forEachIndexed { at, tab ->
                Tab(selected = at == pager.currentPage, onClick = { select(tab) }, text = { Text(label(tab)) })
            }
        }
        if (scrollable) {
            PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = edgePadding) { row() }
        } else {
            PrimaryTabRow(selectedTabIndex = pager.currentPage) { row() }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { at -> tabs.getOrNull(at)?.let { page(it) } }
    }
}

/**
 * A swipe across moves to the [next] or [previous] tab, for a screen whose tabs sit inside its own list
 * (a profile's, under its header) where a pager cannot go. Only a deliberate swipe counts: one that
 * travels [distance] across; a vertical scroll never does. Mirrored right to left.
 */
public fun Modifier.swipeAcross(next: () -> Unit, previous: () -> Unit, distance: Dp = SWIPE_DISTANCE): Modifier =
    pointerInput(Unit) {
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                val far = distance.toPx()
                when {
                    travelled <= -far -> next()
                    travelled >= far -> previous()
                }
            },
        ) { _, dx -> travelled += dx }
    }

/** [swipeAcross] for the reader's direction of writing: towards the end of the line is the next tab. */
@Composable
public fun Modifier.swipeTabs(next: () -> Unit, previous: () -> Unit): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val onNext by rememberUpdatedState(next)
    val onPrevious by rememberUpdatedState(previous)
    return if (rtl) swipeAcross({ onPrevious() }, { onNext() }) else swipeAcross({ onNext() }, { onPrevious() })
}

private val SWIPE_DISTANCE = 72.dp
