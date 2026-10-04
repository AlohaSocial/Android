// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** Taps on the navigation destination already shown, which its screen answers by scrolling to its top. */
public val LocalReselections: ProvidableCompositionLocal<Flow<Unit>> = staticCompositionLocalOf { emptyFlow() }

/**
 * Up to the top of a list, and, tapped again at the top, back to where the reader was, for five minutes.
 * The way up jumps to near the top and scrolls the last [tail] pixels, so a long way up takes no longer
 * than a short one.
 */
@Stable
public class TopScroller(private val list: LazyListState, private val tail: Float, private val now: () -> Long) {
    private var back: Triple<Int, Int, Long>? = null

    /** Up, or back down where the reader was if the list is at its top already; false when nothing moved. */
    public suspend fun toggle(reduced: Boolean): Boolean {
        val atTop = list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        val saved = back?.takeIf { now() - it.third < RETURN_MILLIS }
        when {
            !atTop -> {
                back = Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, now())
                list.scrollToTop(tail, reduced)
            }

            saved != null -> {
                back = null
                list.scrollToItem(saved.first, saved.second)
            }

            else -> return false
        }
        return true
    }

    private companion object {
        const val RETURN_MILLIS = 5 * 60 * 1000L
    }
}

/**
 * To the top: from far away, a jump to within [tail] pixels and a smooth scroll from there; at once where
 * motion is reduced.
 */
public suspend fun LazyListState.scrollToTop(tail: Float, reduced: Boolean) {
    if (reduced) return scrollToItem(0)
    if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > tail) scrollToItem(0, tail.roundToInt())
    animateScrollToItem(0)
}

/** How far the last, smooth part of a scroll to the top goes, in pixels. */
@Composable
public fun topScrollTail(): Float = with(LocalDensity.current) { TAIL.toPx() }

/**
 * [list]'s way to the top and back, with a tick under the finger each time it moves; a tap on the
 * navigation destination already shown ([LocalReselections]) does the same.
 */
@Composable
public fun rememberTopScroll(list: LazyListState): () -> Unit {
    val scope = rememberCoroutineScope()
    val reduced by rememberUpdatedState(rememberReducedMotion())
    val haptics by rememberUpdatedState(rememberHaptics())
    val tail = topScrollTail()
    val scroller = remember(list, tail) { TopScroller(list, tail, SystemClock::elapsedRealtime) }
    val toggle = remember(scroller) {
        {
            scope.launch { if (scroller.toggle(reduced)) haptics(HapticFeedbackType.ContextClick) }
            Unit
        }
    }
    val reselections = LocalReselections.current
    LaunchedEffect(reselections, toggle) { reselections.collect { toggle() } }
    return toggle
}

/** A top bar's title that scrolls its list to the top when tapped. */
@Composable
public fun TopBarTitle(title: String, onScrollToTop: () -> Unit) {
    Text(
        title,
        Modifier.clickable(onClickLabel = stringResource(R.string.list_scroll_to_top), onClick = onScrollToTop),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val TAIL = 300.dp
