// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Account
import social.aloha.core.navigation.ListMembersKey
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.EmptyState
import social.aloha.core.ui.Skeleton

/** Who is in one list: added from those the reader follows and removed here, unless a group keeps them. */
@Composable
public fun ListMembersRoute(
    key: ListMembersKey,
    onProfile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<MembersViewModel, MembersViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        MembersActions(viewModel::onQuery, viewModel::onAdd, viewModel::onRemove, onProfile, viewModel::onRetry, onBack)
    }
    MembersScreen(key.title, state, actions, viewModel::onRefusalShown, modifier)
}

/** What the members screen asks for. */
internal class MembersActions(
    val onQuery: (String) -> Unit,
    val onAdd: (Account) -> Unit,
    val onRemove: (Account) -> Unit,
    val onProfile: (String) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MembersScreen(
    title: String,
    state: MembersUiState,
    actions: MembersActions,
    onRefusalShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    RefusalSnackbar(state.refusal, snackbars, onRefusalShown)
    val pane = stringResource(R.string.lists_members_title, title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = pane },
        topBar = { TopAppBar(title = { Text(pane) }, navigationIcon = { Back(actions.onBack) }) },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.failed && state.members.isEmpty() -> Retry(actions.onRetry)
                state.loading && state.members.isEmpty() -> Skeleton()
                else -> Members(state, actions)
            }
        }
    }
}

@Composable
private fun Members(state: MembersUiState, actions: MembersActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.group) {
            item(key = "group") {
                Text(
                    stringResource(R.string.lists_group_members),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(AlohaSpacing.m),
                )
            }
        } else {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQuery,
                    label = { Text(stringResource(R.string.lists_add_people)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m),
                )
            }
            items(state.found, key = { "f:${it.id}" }) { person ->
                Person(person, actions.onProfile) {
                    IconButton(onClick = { actions.onAdd(person) }) {
                        Icon(AlohaIcons.Add, stringResource(R.string.lists_add, person.bestDisplayName))
                    }
                }
            }
        }
        item(key = "heading") {
            Text(
                stringResource(R.string.lists_members),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s).semantics {
                    heading()
                },
            )
        }
        if (state.members.isEmpty()) item(key = "none") { EmptyState(stringResource(R.string.lists_members_none)) }
        items(state.members, key = { "m:${it.id}" }) { person ->
            Person(person, actions.onProfile) {
                if (!state.group) {
                    IconButton(onClick = { actions.onRemove(person) }) {
                        Icon(AlohaIcons.Close, stringResource(R.string.lists_remove, person.bestDisplayName))
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Person(person: Account, onProfile: (String) -> Unit, trailing: @Composable () -> Unit) {
    ListItem(
        leadingContent = { Avatar(person.avatar, AVATAR) },
        headlineContent = { Text(person.bestDisplayName) },
        supportingContent = { Text(person.qualifiedHandle) },
        trailingContent = trailing,
        modifier = Modifier.clickable { onProfile(person.id) },
    )
}

private val AVATAR = 40.dp
