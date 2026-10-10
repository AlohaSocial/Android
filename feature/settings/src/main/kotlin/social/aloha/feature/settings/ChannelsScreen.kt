// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.VideoChannel
import social.aloha.core.navigation.ChannelsKey
import social.aloha.core.ui.readingColumn

/** The reader's video channels, what a video belongs to on PeerTube; each one can be renamed, more made. */
@Composable
public fun ChannelsRoute(key: ChannelsKey, onConnect: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ChannelsViewModel, ChannelsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChannelsScreen(state, viewModel, onConnect, onBack, modifier)
}

@Composable
internal fun ChannelsScreen(
    state: ChannelsUiState,
    actions: ChannelsActions,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NextcloudPage(
        stringResource(R.string.channels_title),
        state.status,
        onBack,
        onConnect,
        actions::onRetry,
        modifier,
        actions = {
            IconButton(onClick = actions::onNew) { Icon(AlohaIcons.Add, stringResource(R.string.channels_new)) }
        },
    ) { padding ->
        if (state.channels.isEmpty()) {
            Column(
                Modifier.padding(padding).padding(AlohaSpacing.l),
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            ) {
                Text(stringResource(R.string.channels_none), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.channels_none_about), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize().readingColumn()) {
                items(state.channels, key = { it.id }) { channel -> ChannelRow(channel) { actions.onEdit(channel) } }
            }
        }
    }
    state.editing?.let { ChannelEditor(it, state.saving, state.saveFailure, actions) }
}

@Composable
private fun ChannelRow(channel: VideoChannel, onEdit: () -> Unit) {
    ListItem(
        leadingContent = { Icon(AlohaIcons.Video, contentDescription = null) },
        headlineContent = { Text(channel.name.ifBlank { channel.handle }) },
        supportingContent = {
            Column {
                Text("@${channel.handle}")
                if (channel.description.isNotBlank()) {
                    Text(channel.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = channel.videosCount.takeIf { it > 0 }?.let { count ->
            { Text(pluralStringResource(R.plurals.channels_videos, count, count)) }
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.channels_edit_action), onClick = onEdit),
    )
}

/** One dialog for making and renaming; the handle is asked for only while the channel is new. */
@Composable
private fun ChannelEditor(draft: ChannelDraft, saving: Boolean, failure: String?, actions: ChannelsActions) {
    AlertDialog(
        onDismissRequest = actions::onCancel,
        title = { Text(stringResource(if (draft.isNew) R.string.channels_new else R.string.channels_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                if (draft.isNew) {
                    OutlinedTextField(
                        draft.handle,
                        { actions.onDraft(draft.copy(handle = it)) },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.channels_handle)) },
                        supportingText = { Text(stringResource(R.string.channels_handle_about)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                    )
                } else {
                    Text("@${draft.handle}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    draft.name,
                    { actions.onDraft(draft.copy(name = it)) },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.channels_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    draft.description,
                    { actions.onDraft(draft.copy(description = it)) },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.channels_description)) },
                    minLines = 2,
                    maxLines = 6,
                )
                failure?.let {
                    Text(
                        it.ifBlank {
                            stringResource(R.string.channels_save_failed)
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions::onSave, enabled = draft.canSave && !saving) {
                Text(stringResource(R.string.channels_save))
            }
        },
        dismissButton = { TextButton(onClick = actions::onCancel) { Text(stringResource(R.string.channels_cancel)) } },
    )
}
