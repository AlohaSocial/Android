// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.withTimeoutOrNull
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.ui.AccountPicker
import social.aloha.core.ui.LocalActAs

/** Writing a post; a long press, with more than one account signed in, chooses which writes it. */
@Composable
internal fun ComposeButton(chrome: HomeChrome, expanded: Boolean) {
    val accounts = LocalActAs.current?.accounts.orEmpty()
    val composeAs = chrome.onComposeAs?.takeIf { accounts.size > 1 }
    var picking by remember { mutableStateOf(false) }
    val title = stringResource(R.string.timeline_compose_as)
    ExtendedFloatingActionButton(
        onClick = chrome.onCompose,
        expanded = expanded,
        icon = { Icon(AlohaIcons.Compose, contentDescription = null) },
        text = { Text(stringResource(R.string.timeline_compose)) },
        modifier = if (composeAs == null) {
            Modifier
        } else {
            Modifier
                .semantics {
                    onLongClick(title) {
                        picking = true
                        true
                    }
                }
                .longPressFirst { picking = true }
        },
    )
    if (picking && composeAs != null) {
        AccountPicker(title, accounts, onPick = {
            picking = false
            composeAs(it.id)
        }, onDismiss = { picking = false })
    }
}

/**
 * A long press seen before the button underneath sees the touch: once it is long, the rest of the
 * gesture is taken here, so letting go does not also click.
 */
private fun Modifier.longPressFirst(onLongPress: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial)
            }
            if (lifted != null) return@awaitEachGesture
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    },
)
