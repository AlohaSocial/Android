// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.ComposerKey

/** The composer for [key]; [onDone] leaves it, once posted or discarded. */
@Composable
public fun ComposerRoute(key: ComposerKey, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val done by rememberUpdatedState(onDone)
    var discarding by rememberSaveable { mutableStateOf(false) }
    val close = { if (viewModel.hasWriting && state.posted == 0) discarding = true else done() }

    BackHandler(onBack = close)
    LaunchedEffect(state.done) { if (state.done) done() }
    FailureSnackbar(state.failure, snackbars, viewModel::onFailureShown)

    val actions = remember(viewModel) {
        object : ComposerActions {
            override fun onClose() = close()

            override fun onPost() = viewModel.onPost()

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
        }
    }
    ComposerScreen(state, viewModel.segments, viewModel.spoiler, actions, modifier, snackbars)

    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.composer_discard_title)) },
            text = { Text(stringResource(R.string.composer_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    discarding = false
                    done()
                }) { Text(stringResource(R.string.composer_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.composer_keep_editing)) }
            },
        )
    }
}

@Composable
private fun FailureSnackbar(failure: PostFailure?, snackbars: SnackbarHostState, onShown: () -> Unit) {
    val message = when (failure) {
        is PostFailure.Refused -> failure.message?.let { stringResource(R.string.composer_refused, it) }
            ?: stringResource(R.string.composer_refused_unknown)

        is PostFailure.Unreached -> stringResource(R.string.composer_unreached)

        PostFailure.ReplyNotFound -> stringResource(R.string.composer_reply_not_found)

        null -> null
    }
    LaunchedEffect(failure) {
        if (message != null) {
            onShown()
            snackbars.showSnackbar(message)
        }
    }
}
