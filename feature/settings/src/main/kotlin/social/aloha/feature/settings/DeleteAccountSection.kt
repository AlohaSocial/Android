// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.openInBrowser

/**
 * Deleting the account in use, for good. On Nextcloud Social it happens here, once the Nextcloud is
 * connected, after the handle is typed; elsewhere the server's own website does it.
 */
internal object DeleteAccountSection : SettingsSection {
    override val key: String = "delete-account"
    override val order: Int = 950
    override val title: Int = R.string.settings_delete
    override val icon: ImageVector = AlohaIcons.Delete

    @Composable
    override fun Content() {
        val viewModel: DeleteAccountViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        DeleteAccountRows(
            state,
            onDelete = viewModel::onDelete,
            onWebPage = { state.webPage?.let { openInBrowser(context, it) } },
            onRefusalShown = viewModel::onRefusalShown,
        )
    }
}

@Composable
internal fun DeleteAccountRows(
    state: DeleteAccountUiState,
    onDelete: (typed: String) -> Unit,
    onWebPage: () -> Unit,
    onRefusalShown: () -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = AlohaSpacing.l, vertical = AlohaSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
    ) {
        when (state.mode) {
            DeletionMode.InApp -> {
                Text(stringResource(R.string.settings_delete_about, state.handle))
                if (state.deleting) {
                    Text(
                        stringResource(R.string.settings_delete_deleting),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                state.refusal?.let {
                    Text(
                        refusalText(it),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = {
                        onRefusalShown()
                        asking = true
                    },
                    enabled = !state.deleting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_delete_start)) }
            }

            DeletionMode.NeedsNextcloud -> Text(stringResource(R.string.settings_delete_needs_nextcloud))

            DeletionMode.OnTheWeb -> {
                Text(stringResource(R.string.settings_delete_on_web))
                if (state.webPage != null) {
                    OutlinedButton(onClick = onWebPage) { Text(stringResource(R.string.settings_delete_open_web)) }
                }
            }
        }
    }
    if (asking) {
        ConfirmDialog(state.handle, onDismiss = { asking = false }) {
            asking = false
            onDelete(it)
        }
    }
}

/** Asks for the handle, typed out, before anything goes; Delete stays off until it matches. */
@Composable
private fun ConfirmDialog(handle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val matches = DeleteAccountViewModel.confirms(typed, handle)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m)) {
                Text(stringResource(R.string.settings_delete_body, handle))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.settings_delete_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (matches) onConfirm(typed) }),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(typed) },
                enabled = matches,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.settings_delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_delete_cancel)) } },
    )
}

@Composable
private fun refusalText(refusal: DeletionRefusal): String = when (refusal) {
    is DeletionRefusal.Server -> refusal.message
    DeletionRefusal.TooOften -> stringResource(R.string.settings_delete_too_often)
    DeletionRefusal.Failed -> stringResource(R.string.settings_delete_failed)
}
