// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import social.aloha.core.designsystem.AlohaIcons

/**
 * Boost, with a menu where the screen quotes: boost or undo it, and quote, ask to quote, or see why the
 * author allows no quote. Elsewhere a tap boosts at once.
 */
@Composable
internal fun BoostButton(row: StatusRowUi, actions: StatusActions, button: @Composable (onClick: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (!actions.quotes) return button { actions.onBoost(row) }
    Box {
        button { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (row.state.boosted) R.string.status_action_unboost else R.string.status_action_boost,
                        ),
                    )
                },
                leadingIcon = { Icon(AlohaIcons.Boost, contentDescription = null) },
                onClick = {
                    open = false
                    actions.onBoost(row)
                },
            )
            DropdownMenuItem(
                text = { QuoteLabel(row.quoteAccess) },
                leadingIcon = { Icon(AlohaIcons.Quote, contentDescription = null) },
                enabled = row.quoteAccess != QuoteAccess.Denied,
                onClick = {
                    open = false
                    actions.onQuote(row)
                },
            )
        }
    }
}

@Composable
private fun QuoteLabel(access: QuoteAccess) {
    if (access != QuoteAccess.Denied) return Text(stringResource(quoteLabel(access)))
    Column {
        Text(stringResource(R.string.status_action_quote))
        Text(stringResource(R.string.status_quote_denied), style = MaterialTheme.typography.bodySmall)
    }
}

/** What the quote item says: a quote, or a request where the author approves each one. */
internal fun quoteLabel(access: QuoteAccess): Int =
    if (access == QuoteAccess.Request) R.string.status_action_request_quote else R.string.status_action_quote
