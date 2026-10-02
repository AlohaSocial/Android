// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.navigation.BlockedKey
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.readingColumn

/** Who the reader blocked and muted, and the servers they blocked, each to be taken back. */
@Composable
public fun BlockedRoute(key: BlockedKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<BlockedViewModel, BlockedViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BlockedScreen(
        state,
        BlockedActions(viewModel::unblock, viewModel::unmute, viewModel::unblockServer, viewModel::blockServer),
        viewModel::onRefusalShown,
        onBack,
        modifier,
    )
}

/** What the screen changes. */
internal class BlockedActions(
    val onUnblock: (String) -> Unit = {},
    val onUnmute: (String) -> Unit = {},
    val onUnblockServer: (String) -> Unit = {},
    val onBlockServer: (String) -> Boolean = { false },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockedScreen(
    state: BlockedState,
    actions: BlockedActions,
    onRefusalShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: BlockedTab = BlockedTab.Blocked,
) {
    val title = stringResource(R.string.blocked_title)
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.blocked_refused)
    LaunchedEffect(state.refused) {
        if (state.refused) {
            snackbars.showSnackbar(refused)
            onRefusalShown()
        }
    }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.blocked_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                BlockedTab.entries.forEach {
                    Tab(selected = it == tab, onClick = { tab = it }, text = { Text(stringResource(tabTitle(it))) })
                }
            }
            when (tab) {
                BlockedTab.Blocked -> People(
                    state.blocked,
                    state.failed,
                    R.string.blocked_none,
                    R.string.blocked_unblock,
                    actions.onUnblock,
                )

                BlockedTab.Muted -> People(
                    state.muted,
                    state.failed,
                    R.string.blocked_none_muted,
                    R.string.blocked_unmute,
                    actions.onUnmute,
                )

                BlockedTab.Servers -> Servers(state.servers, state.failed, actions)
            }
        }
    }
}

private fun tabTitle(tab: BlockedTab): Int = when (tab) {
    BlockedTab.Blocked -> R.string.blocked_tab_blocked
    BlockedTab.Muted -> R.string.blocked_tab_muted
    BlockedTab.Servers -> R.string.blocked_tab_servers
}

@Composable
private fun People(people: List<Kept>?, failed: Boolean, none: Int, action: Int, onTakeBack: (String) -> Unit) {
    when {
        people == null && failed -> Note(stringResource(R.string.blocked_failed))

        people == null -> Loading()

        people.isEmpty() -> Note(stringResource(none))

        else -> LazyColumn(Modifier.readingColumn().fillMaxSize()) {
            items(people, key = { it.id }) { person ->
                ListItem(
                    leadingContent = { Avatar(person.avatarUrl, AVATAR) },
                    headlineContent = { Text(person.name.ifBlank { person.handle }) },
                    supportingContent = { Text(person.handle) },
                    trailingContent = {
                        TextButton(onClick = { onTakeBack(person.id) }) { Text(stringResource(action)) }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Servers(servers: List<String>?, failed: Boolean, actions: BlockedActions) {
    var typed by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val add = {
        if (actions.onBlockServer(typed)) typed = "" else invalid = true
    }
    LazyColumn(Modifier.readingColumn().fillMaxSize()) {
        item {
            Row(Modifier.padding(AlohaSpacing.m), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = {
                        typed = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.blocked_server_field)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (invalid) R.string.blocked_server_invalid else R.string.blocked_server_help,
                            ),
                        )
                    },
                    isError = invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = add, enabled = typed.isNotBlank()) {
                    Text(stringResource(R.string.blocked_server_block))
                }
            }
        }
        when {
            servers == null && failed -> item { Note(stringResource(R.string.blocked_failed)) }

            servers == null -> item { Loading() }

            servers.isEmpty() -> item { Note(stringResource(R.string.blocked_none_servers)) }

            else -> items(servers, key = { it }) { domain ->
                ListItem(
                    headlineContent = { Text(domain) },
                    trailingContent = {
                        TextButton(onClick = { actions.onUnblockServer(domain) }) {
                            Text(stringResource(R.string.blocked_unblock))
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.l),
    )
}

@Composable
private fun Loading() {
    CircularProgressIndicator(Modifier.padding(AlohaSpacing.l))
}

private val AVATAR = 40.dp
