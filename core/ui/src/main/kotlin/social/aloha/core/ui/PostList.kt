// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import social.aloha.core.designsystem.AlohaSpacing

/** The time once a minute, which is as fine as a post's age is shown. */
public fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(MINUTE_MILLIS)
    }
}

/** Calls [onNearEnd] each time the list shows one of its last ten rows, [count] being how many it has. */
@Composable
public fun NearEndEffect(listState: LazyListState, count: Int, onNearEnd: () -> Unit) {
    val size by rememberUpdatedState(count)
    val call by rememberUpdatedState(onNearEnd)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { last -> size > 0 && last >= size - NEAR_END }
            .collect { call() }
    }
}

/** A spinner for rows on their way, said aloud once as it appears so a reader not looking knows too. */
@Composable
public fun ListProgress(modifier: Modifier = Modifier, size: Dp? = null) {
    val label = stringResource(R.string.list_loading)
    Box(modifier.fillMaxWidth().padding(AlohaSpacing.m), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            (size?.let { Modifier.size(it) } ?: Modifier).semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val NEAR_END = 10
