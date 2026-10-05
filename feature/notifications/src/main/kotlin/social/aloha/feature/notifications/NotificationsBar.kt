// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.badgeCount

/** One action of the bar, and the count its badge shows, if any. */
private class BarAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val badge: Int = 0)

/**
 * The bar's actions. A bar narrower than [ROOMY], a phone's or a pane's beside another, keeps them all
 * behind ⋮ so the title keeps its one line, the ⋮ wearing the badge of the requests waiting; a wider
 * one shows them as icons.
 */
@Composable
internal fun BarActions(state: NotificationsUiState, actions: NotificationsActions, roomy: Boolean) {
    val pending = state.pendingRequests
    val all = listOfNotNull(
        // with a kind chosen, "all" would mark read what the reader never saw
        BarAction(stringResource(R.string.notifications_mark_all_read), AlohaIcons.MarkAllRead, actions::onMarkAllRead)
            .takeIf { state.kinds.isEmpty() },
        BarAction(stringResource(R.string.notifications_refresh), AlohaIcons.Retry, actions::onRefresh),
        BarAction(
            if (pending > 0) {
                pluralStringResource(R.plurals.notifications_requests_action, pending, pending)
            } else {
                stringResource(R.string.notifications_requests_open)
            },
            AlohaIcons.Filtered,
            actions::onRequests,
            badge = pending,
        ).takeIf { state.filtering },
        BarAction(stringResource(R.string.notifications_policy_action), AlohaIcons.Rules, actions::onPolicy)
            .takeIf { state.filtering },
    )
    if (roomy) {
        all.forEach { action ->
            IconButton(onClick = action.onClick) { Badged(action.badge) { Icon(action.icon, action.label) } }
        }
    } else {
        Overflow(all)
    }
}

@Composable
private fun Overflow(all: List<BarAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Badged(all.sumOf { it.badge }) { Icon(AlohaIcons.More, stringResource(R.string.notifications_more)) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    trailingIcon = if (action.badge > 0) ({ Badge { Text(badgeCount(action.badge)) } }) else null,
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun Badged(count: Int, content: @Composable () -> Unit) {
    BadgedBox(badge = { if (count > 0) Badge { Text(badgeCount(count)) } }) { content() }
}

/** The width from which the bar shows its actions as icons: Material's medium window width. */
internal val ROOMY = 600.dp
