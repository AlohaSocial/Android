// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AccountList
import social.aloha.core.model.ListRepliesPolicy
import social.aloha.core.navigation.ListsKey

/** The reader's lists: each opens its timeline; made, set and deleted here, but for those a group keeps. */
@Composable
public fun ListsRoute(key: ListsKey, onOpen: (AccountList) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ListsViewModel, ListsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        ListsActions(
            onOpen = onOpen,
            onCreate = viewModel::onCreate,
            onUpdate = viewModel::onUpdate,
            onDelete = viewModel::onDelete,
            onRetry = viewModel::onRetry,
            onBack = onBack,
        )
    }
    ListsScreen(state, actions, viewModel::onRefusalShown, modifier)
}

/** What the lists screen asks for. */
internal class ListsActions(
    val onOpen: (AccountList) -> Unit,
    val onCreate: (String) -> Unit,
    val onUpdate: (AccountList, String, ListRepliesPolicy?, Boolean) -> Unit,
    val onDelete: (AccountList) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListsScreen(
    state: ListsUiState,
    actions: ListsActions,
    onRefusalShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.lists_title)
    val snackbars = remember { SnackbarHostState() }
    RefusalSnackbar(state.refusal, snackbars, onRefusalShown)
    var editing by remember { mutableStateOf<AccountList?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountList?>(null) }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { Back(actions.onBack) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(AlohaIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.lists_new)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.lists.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.lists, key = { it.id }) { list ->
                        ListRow(list, actions.onOpen, onEdit = { editing = list }, onDelete = { deleting = list })
                        HorizontalDivider()
                    }
                }

                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.failed -> Retry(actions.onRetry)

                else -> Message(stringResource(R.string.lists_none))
            }
        }
    }
    if (creating) {
        TitleDialog(null, onDismiss = { creating = false }) { name, _, _ ->
            creating = false
            actions.onCreate(name)
        }
    }
    editing?.let { list ->
        TitleDialog(list, onDismiss = { editing = null }) { name, policy, exclusive ->
            editing = null
            actions.onUpdate(list, name, policy, exclusive)
        }
    }
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.lists_delete_title, list.title)) },
            text = { Text(stringResource(R.string.lists_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    actions.onDelete(list)
                }) { Text(stringResource(R.string.lists_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** One list; a group's says so and offers nothing to change, since the group is changed where it lives. */
@Composable
private fun ListRow(list: AccountList, onOpen: (AccountList) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = {
            Icon(if (list.followsGroup) AlohaIcons.Group else AlohaIcons.Lists, contentDescription = null)
        },
        headlineContent = { Text(list.title) },
        supportingContent = if (list.followsGroup) {
            { Text(stringResource(R.string.lists_group)) }
        } else {
            null
        },
        trailingContent = if (list.followsGroup) {
            null
        } else {
            {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(AlohaIcons.More, stringResource(R.string.lists_options, list.title))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.lists_edit)) }, onClick = {
                            menu = false
                            onEdit()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.lists_delete)) }, onClick = {
                            menu = false
                            onDelete()
                        })
                    }
                }
            }
        },
        modifier = Modifier.clickable { onOpen(list) },
    )
}

/**
 * A list's name and, for one being edited, whose replies it shows and whether its members leave the
 * home timeline. [onSave] hears the three.
 */
@Composable
private fun TitleDialog(
    list: AccountList?,
    onDismiss: () -> Unit,
    onSave: (String, ListRepliesPolicy?, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(list?.title.orEmpty()) }
    var policy by rememberSaveable {
        mutableStateOf(
            ListRepliesPolicy.entries.firstOrNull {
                it.wire ==
                    list?.repliesPolicy
            },
        )
    }
    var exclusive by rememberSaveable { mutableStateOf(list?.exclusive == true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (list == null) R.string.lists_new else R.string.lists_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.lists_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (list !=
                    null
                ) {
                    Settings(policy, exclusive, onPolicy = { policy = it }, onExclusive = { exclusive = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, policy, exclusive) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.lists_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun Settings(
    policy: ListRepliesPolicy?,
    exclusive: Boolean,
    onPolicy: (ListRepliesPolicy) -> Unit,
    onExclusive: (Boolean) -> Unit,
) {
    Text(
        stringResource(R.string.lists_replies),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = AlohaSpacing.m, bottom = AlohaSpacing.xs),
    )
    Column(Modifier.selectableGroup()) {
        ListRepliesPolicy.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(option == policy, role = Role.RadioButton) { onPolicy(option) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == policy, onClick = null)
                Text(stringResource(policyName(option)), Modifier.padding(start = AlohaSpacing.s))
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = AlohaSpacing.s).selectable(exclusive, role = Role.Switch) {
            onExclusive(!exclusive)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.lists_exclusive), Modifier.weight(1f))
        Switch(checked = exclusive, onCheckedChange = null)
    }
}

private fun policyName(policy: ListRepliesPolicy): Int = when (policy) {
    ListRepliesPolicy.Followed -> R.string.lists_replies_followed
    ListRepliesPolicy.List -> R.string.lists_replies_list
    ListRepliesPolicy.None -> R.string.lists_replies_none
}

@Composable
internal fun Back(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.lists_back)) }
}

@Composable
internal fun Retry(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(AlohaSpacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.lists_failed), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = AlohaSpacing.m)) {
            Text(stringResource(R.string.lists_retry))
        }
    }
}

@Composable
internal fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A refused change, in the server's own words where it gave them. */
@Composable
internal fun RefusalSnackbar(refusal: Refusal?, snackbars: SnackbarHostState, onShown: () -> Unit) {
    val failed = stringResource(R.string.lists_change_failed)
    LaunchedEffect(refusal) {
        val text = when (refusal) {
            is Refusal.Said -> refusal.message
            Refusal.Failed -> failed
            null -> return@LaunchedEffect
        }
        onShown()
        snackbars.showSnackbar(text)
    }
}
