// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A click with a mouse, a pointer remote or a touch, which Compose for TV's buttons and cards do not take:
 * they answer the D-pad's centre and Enter only. The same [onClick] then runs for both.
 */
@Composable
internal fun Modifier.tap(onClick: () -> Unit): Modifier {
    val current by rememberUpdatedState(onClick)
    return pointerInput(Unit) { detectTapGestures { current() } }
}
