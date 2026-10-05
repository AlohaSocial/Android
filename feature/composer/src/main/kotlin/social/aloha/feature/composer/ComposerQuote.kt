// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing

/** Under the opening post: the post it quotes, with an X to drop it, and what the composer says of it once. */
@Composable
internal fun QuoteParts(state: ComposerUiState, actions: ComposerActions) {
    state.quote?.let { quote ->
        OutlinedCard(Modifier.fillMaxWidth().padding(top = AlohaSpacing.xs)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(AlohaSpacing.s)) {
                    Text(quote.author, style = MaterialTheme.typography.titleSmall)
                    Text(
                        quote.excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = EXCERPT_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = actions::onDropQuote) {
                    Icon(AlohaIcons.Close, stringResource(R.string.composer_quote_remove))
                }
            }
        }
    }
    val notice = state.quoteNotice ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(start = AlohaSpacing.s), verticalAlignment = Alignment.CenterVertically) {
            val text = if (notice ==
                QuoteNotice.Unlisted
            ) {
                R.string.composer_quote_unlisted
            } else {
                R.string.composer_quote_linked
            }
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(vertical = AlohaSpacing.xs),
            )
            TextButton(onClick = actions::onQuoteNoticeShown) { Text(stringResource(R.string.composer_quote_got_it)) }
        }
    }
}

/** Before someone else's followers-only post goes out quoted: quote it, or not; and whether to ask again. */
@Composable
internal fun QuoteConfirmDialog(onConfirm: (dontAsk: Boolean) -> Unit, onCancel: () -> Unit) {
    var dontAsk by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.composer_quote_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.composer_quote_confirm_body))
                Row(
                    Modifier.toggleable(dontAsk, role = Role.Checkbox) { dontAsk = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(dontAsk, onCheckedChange = null)
                    Text(stringResource(R.string.composer_quote_dont_ask), Modifier.padding(start = AlohaSpacing.xs))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontAsk) }) { Text(stringResource(R.string.composer_quote_confirm)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.composer_quote_cancel)) } },
    )
}

private const val EXCERPT_LINES = 4
