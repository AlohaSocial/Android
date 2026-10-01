// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Account
import social.aloha.core.navigation.NewMessageKey
import social.aloha.core.ui.Avatar

/** Whom a new direct message is for; [onPick] gets their handle, which the message starts with. */
@Composable
public fun NewMessageRoute(
    key: NewMessageKey,
    onPick: (acct: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<NewMessageViewModel, NewMessageViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NewMessageScreen(state, viewModel::onQuery, { onPick(it.acct) }, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewMessageScreen(
    state: NewMessageUiState,
    onQuery: (String) -> Unit,
    onPick: (Account) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.conversations_new)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.conversations_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "query") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    label = { Text(stringResource(R.string.conversations_to)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m),
                )
            }
            if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.query.isBlank() && !state.loading) {
                item(key = "note") {
                    val none = state.mutuals.isEmpty()
                    val note = if (none) R.string.conversations_no_mutuals else R.string.conversations_mutuals
                    Text(
                        stringResource(note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
                    )
                }
            }
            items(state.shown, key = { it.id }) { person ->
                ListItem(
                    modifier = Modifier.clickable { onPick(person) },
                    leadingContent = { Avatar(person.avatar, AVATAR) },
                    headlineContent = { Text(person.bestDisplayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(person.qualifiedHandle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
    }
}

private val AVATAR = 40.dp
