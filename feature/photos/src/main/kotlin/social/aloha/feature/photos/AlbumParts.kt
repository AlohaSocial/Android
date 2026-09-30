// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.ListProgress

/**
 * Asks for an album's name, and for a new one what it holds too. [confirm] stays off until there is a
 * name, which is the one thing an album needs.
 */
@Composable
internal fun AlbumNameDialog(
    title: String,
    initial: String,
    askDescription: Boolean,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                OutlinedTextField(
                    name,
                    onValueChange = { name = it.take(MAXIMUM_TITLE) },
                    label = { Text(stringResource(R.string.albums_name)) },
                    singleLine = true,
                )
                if (askDescription) {
                    OutlinedTextField(
                        description,
                        onValueChange = { description = it.take(MAXIMUM_DESCRIPTION) },
                        label = { Text(stringResource(R.string.albums_description)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm(name, description)
                },
                enabled = name.isNotBlank(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.albums_cancel)) } },
    )
}

/** Under a list: a spinner while it loads, a retry when it could not, or what an empty one says. */
@Composable
internal fun ListFooter(loading: Boolean, failed: Boolean, empty: Boolean, emptyText: String, onRetry: () -> Unit) {
    when {
        failed -> Column(
            Modifier.fillMaxWidth().padding(AlohaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.albums_failed), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) { Text(stringResource(R.string.albums_retry)) }
        }

        loading -> ListProgress()

        empty -> Box(Modifier.fillMaxWidth().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// Pixelfed's limits on an album's title and description
private const val MAXIMUM_TITLE = 50
private const val MAXIMUM_DESCRIPTION = 500
