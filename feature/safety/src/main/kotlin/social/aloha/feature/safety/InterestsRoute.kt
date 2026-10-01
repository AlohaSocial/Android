// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.InterestTag
import social.aloha.core.model.InterestsState
import social.aloha.core.navigation.InterestsKey
import social.aloha.core.ui.SwitchRow

/**
 * The hashtags Nextcloud Social has learnt the reader cares about, and those it is still weighing;
 * each pinned so it stays, or taken out; and whether it learns at all.
 */
@Composable
public fun InterestsRoute(key: InterestsKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<InterestsViewModel, InterestsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InterestsScreen(state, viewModel, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InterestsScreen(
    state: InterestsUiState,
    actions: InterestsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.interests_title)
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.interests_change_failed)
    LaunchedEffect(state.changeFailed) {
        if (state.changeFailed) {
            actions.onChangeFailureShown()
            snackbars.showSnackbar(refused)
        }
    }
    var resetting by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.safety_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        val interests = state.interests
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                interests == null && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                interests == null -> Text(
                    stringResource(
                        if (state.unavailable) R.string.interests_unavailable else R.string.interests_failed,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(AlohaSpacing.l),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    interestsContent(interests, actions) { resetting = true }
                }
            }
        }
    }
    if (resetting) {
        AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text(stringResource(R.string.interests_reset_title)) },
            text = { Text(stringResource(R.string.interests_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    resetting = false
                    actions.onReset()
                }) { Text(stringResource(R.string.interests_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { resetting = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

private fun LazyListScope.interestsContent(state: InterestsState, actions: InterestsActions, onReset: () -> Unit) {
    item(key = "learning") {
        SwitchRow(
            stringResource(R.string.interests_learning),
            state.settings.learning,
            actions::onLearning,
            summary = stringResource(R.string.interests_learning_summary),
        )
    }
    if (state.settings.learning) {
        item(key = "paused") {
            SwitchRow(
                stringResource(R.string.interests_paused),
                state.settings.paused,
                actions::onPaused,
                summary = stringResource(R.string.interests_paused_summary),
            )
        }
    }
    heading(R.string.interests_yours)
    if (state.thin) item(key = "thin") { Note(stringResource(R.string.interests_thin)) }
    items(state.interests, key = { "i:${it.id}" }) { tag -> Interest(tag, actions) }
    item(key = "add") { AddField(R.string.interests_hashtag, R.string.interests_add, actions::onAdd) }
    candidates(state.candidates, actions::onAdd)
    item(key = "reset") {
        TextButton(onClick = onReset, modifier = Modifier.padding(AlohaSpacing.s)) {
            Text(stringResource(R.string.interests_reset))
        }
    }
}

/** What the server is still weighing, each added with a tap. */
private fun LazyListScope.candidates(candidates: List<InterestTag>, onAdd: (String) -> Unit) {
    if (candidates.isEmpty()) return
    heading(R.string.interests_candidates)
    items(candidates, key = { "c:${it.id}" }) { tag ->
        ListItem(
            headlineContent = { Text("#${tag.tag}") },
            trailingContent = {
                TextButton(onClick = { onAdd(tag.tag) }) { Text(stringResource(R.string.interests_add)) }
            },
        )
    }
}

/** One interest: pinned so learning never drops it, which it says in words too, or not; and taken out. */
@Composable
private fun Interest(tag: InterestTag, actions: InterestsActions) {
    ListItem(
        headlineContent = { Text("#${tag.tag}") },
        supportingContent = if (tag.pinned) ({ Text(stringResource(R.string.interests_kept)) }) else null,
        trailingContent = {
            Row {
                IconToggleButton(checked = tag.pinned, onCheckedChange = { actions.onPin(tag.tag, it) }) {
                    Icon(AlohaIcons.Pinned, stringResource(R.string.interests_pin, tag.tag))
                }
                IconButton(onClick = { actions.onRemove(tag.tag) }) {
                    Icon(AlohaIcons.Remove, stringResource(R.string.interests_remove, tag.tag))
                }
            }
        },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
    )
}
