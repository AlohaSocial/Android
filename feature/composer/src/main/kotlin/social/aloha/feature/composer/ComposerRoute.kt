// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.ComposerKey

/** Which of the composer's dialogs is open; saved, so turning the phone keeps it open. */
private class Dialogs(
    val editing: MutableState<String?>,
    val undescribed: MutableState<Boolean>,
    val discarding: MutableState<Boolean>,
)

/** The composer for [key]; [onDone] leaves it, once posted or discarded. */
@Composable
public fun ComposerRoute(key: ComposerKey, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val done by rememberUpdatedState(onDone)
    val dialogs = Dialogs(
        rememberSaveable { mutableStateOf(null) },
        rememberSaveable { mutableStateOf(false) },
        rememberSaveable { mutableStateOf(false) },
    )
    val actions = rememberActions(viewModel, state, dialogs) { done() }
    val hue = MaterialTheme.colorScheme.primary.toArgb()
    LaunchedEffect(hue) { viewModel.cards.onHue(hue) }

    BackHandler(onBack = actions::onClose)
    LaunchedEffect(state.done) { if (state.done) done() }
    FailureSnackbar(state, snackbars, viewModel::onFailureShown)
    ComposerScreen(state, viewModel.segments, viewModel.spoiler, actions, modifier, snackbars)
    ComposerDialogs(state, viewModel, dialogs) { done() }
}

/**
 * The composer's actions, made once; what they decide on (the state, what the pickers accept) is
 * read as it is when they run, never as it was when they were made.
 */
@Composable
private fun rememberActions(
    viewModel: ComposerViewModel,
    state: ComposerUiState,
    dialogs: Dialogs,
    done: () -> Unit,
): ComposerActions {
    val current by rememberUpdatedState(state)
    val types by rememberUpdatedState(viewModel.acceptedTypes)
    val pickMedia = rememberLauncherForActivityResult(
        // the picker takes at least two, and the ViewModel keeps to the room there is
        ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, state.maxAttachments)),
        viewModel::onPicked,
    )
    val pickFiles =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), viewModel::onPicked)
    return remember(viewModel, dialogs) {
        object : ComposerActions {
            override fun onClose() {
                if (viewModel.hasWriting && current.posted == 0) dialogs.discarding.value = true else done()
            }

            override fun onPost() {
                if (current.warnMissingDescription && current.undescribed) {
                    dialogs.undescribed.value = true
                } else {
                    viewModel.onPost()
                }
            }

            override fun onText(index: Int, value: TextFieldValue) = viewModel.onText(index, value)

            override fun onSpoiler(shown: Boolean, text: String) = viewModel.onSpoiler(shown, text)

            override fun onVisibility(visibility: Visibility) = viewModel.onVisibility(visibility)

            override fun onLanguage(language: String?) = viewModel.onLanguage(language)

            override fun onQuotePolicy(policy: QuotePolicy) = viewModel.onQuotePolicy(policy)

            override fun onSuggestion(suggestion: Suggestion) = viewModel.onSuggestion(suggestion)

            override fun onEmoji(emoji: CustomEmoji) = viewModel.onEmoji(emoji)

            override fun onAddSegment() = viewModel.onSegments()

            override fun onRemoveSegment(index: Int) = viewModel.onSegments(index)

            override fun onAuthor(id: String) = viewModel.onAuthor(id)

            override fun onPickMedia() =
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

            override fun onPickFiles() = pickFiles.launch(types)

            override fun onEditMedia(id: String) {
                dialogs.editing.value = id
            }

            override fun onRemoveMedia(id: String) = viewModel.attachments.remove(id)

            override fun onRetryMedia(id: String) = viewModel.attachments.retry(id)

            override fun onSensitive(sensitive: Boolean) {
                viewModel.attachments.sensitive.value = sensitive
            }

            override fun onCard(on: Boolean) {
                viewModel.cards.onCard(on)
                if (on) viewModel.cards.onText(viewModel.segments.first().text)
            }

            override fun onCardBackground(index: Int) {
                viewModel.cards.onBackground(index)
                viewModel.cards.onText(viewModel.segments.first().text)
            }
        }
    }
}

@Composable
private fun ComposerDialogs(state: ComposerUiState, viewModel: ComposerViewModel, dialogs: Dialogs, done: () -> Unit) {
    var editing by dialogs.editing
    var undescribed by dialogs.undescribed
    var discarding by dialogs.discarding
    editing?.let { id ->
        // an attachment removed while its sheet was open closes the sheet
        val attachment = state.attachments.flatten().firstOrNull { it.id == id }
        if (attachment == null) {
            editing = null
        } else {
            MediaEditor(attachment) { description, focus, filter ->
                viewModel.attachments.describe(id, description, focus)
                viewModel.attachments.applyFilter(id, filter)
                editing = null
            }
        }
    }
    if (undescribed) {
        UndescribedDialog(
            onDescribe = {
                undescribed = false
                editing = state.attachments.flatten().firstOrNull { it.description.isBlank() }?.id
            },
            onPostAnyway = {
                undescribed = false
                viewModel.onPost()
            },
        )
    }
    if (discarding) {
        DiscardDialog(
            onDiscard = {
                discarding = false
                done()
            },
            onKeep = { discarding = false },
        )
    }
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.composer_discard_title)) },
        text = { Text(stringResource(R.string.composer_discard_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.composer_discard)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.composer_keep_editing)) } },
    )
}

/** A warning, never a block: posting without descriptions stays one tap away. */
@Composable
private fun UndescribedDialog(onDescribe: () -> Unit, onPostAnyway: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDescribe,
        title = { Text(stringResource(R.string.composer_undescribed_title)) },
        text = { Text(stringResource(R.string.composer_undescribed_body)) },
        confirmButton = {
            TextButton(onClick = onDescribe) { Text(stringResource(R.string.composer_undescribed_add)) }
        },
        dismissButton = {
            TextButton(onClick = onPostAnyway) { Text(stringResource(R.string.composer_undescribed_post)) }
        },
    )
}

@Composable
private fun FailureSnackbar(state: ComposerUiState, snackbars: SnackbarHostState, onShown: () -> Unit) {
    val message = attachMessage(state.attachFailure) ?: postMessage(state.failure)
    LaunchedEffect(state.failure, state.attachFailure) {
        if (message != null) {
            onShown()
            snackbars.showSnackbar(message)
        }
    }
}

@Composable
private fun attachMessage(failure: AttachFailure?): String? = when (failure) {
    is AttachFailure.Unsupported -> stringResource(R.string.composer_attach_unsupported)

    is AttachFailure.TooLarge ->
        stringResource(R.string.composer_attach_too_large, Attachments.size(failure.limitBytes))

    AttachFailure.Unreadable -> stringResource(R.string.composer_attach_unreadable)

    null -> null
}

@Composable
private fun postMessage(failure: PostFailure?): String? = when (failure) {
    is PostFailure.Refused -> failure.message?.let { stringResource(R.string.composer_refused, it) }
        ?: stringResource(R.string.composer_refused_unknown)

    is PostFailure.Unreached -> stringResource(R.string.composer_unreached)

    PostFailure.ReplyNotFound -> stringResource(R.string.composer_reply_not_found)

    PostFailure.CardFailed -> stringResource(R.string.composer_card_failed)

    null -> null
}
