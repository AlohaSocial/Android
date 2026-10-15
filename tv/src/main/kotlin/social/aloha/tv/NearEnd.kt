// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Asks for more once [lastVisible], a lazy layout's last item in view, comes within [margin] of [count], and
 * again once a page that was [loading] arrived: one that brought nothing for this layout leaves the reader
 * where they were, near the end, with nothing new to scroll to.
 */
@Composable
internal fun NearEnd(count: Int, margin: Int, loading: Boolean, lastVisible: () -> Int?, onNearEnd: () -> Unit) {
    LaunchedEffect(count, loading) {
        if (loading) return@LaunchedEffect
        snapshotFlow { lastVisible() ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (count > 0 && last >= count - margin) onNearEnd() }
    }
}
