// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Conversation
import social.aloha.core.navigation.ConversationsKey
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.PostTime
import social.aloha.core.ui.short

/**
 * The reader's direct conversations: who with, the last message, and whether it is unread. One opens
 * the thread of its last message; a new one starts with someone chosen in [onNew].
 */
@Composable
public fun ConversationsRoute(
    key: ConversationsKey,
    onThread: (statusId: String) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<ConversationsViewModel, ConversationsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    val actions = remember(viewModel) {
        ConversationsActions(
            onOpen = { conversation ->
                viewModel.onOpened(conversation.id)
                conversation.lastStatus?.let { onThread(it.id) }
            },
            onDelete = viewModel::onDelete,
            onReadAll = viewModel::onReadAll,
            onMore = viewModel::onMore,
            onNew = onNew,
            onBack = onBack,
        )
    }
    ConversationsScreen(state, actions, viewModel::onChangeFailureShown, modifier)
}

/** What the conversations screen asks for. */
internal class ConversationsActions(
    val onOpen: (Conversation) -> Unit,
    val onDelete: (String) -> Unit,
    val onReadAll: () -> Unit,
    val onMore: () -> Unit,
    val onNew: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationsScreen(
    state: ConversationsUiState,
    actions: ConversationsActions,
    onChangeFailureShown: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = remember { Instant.now() },
) {
    val title = stringResource(R.string.conversations_title)
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.conversations_change_failed)
    LaunchedEffect(state.changeFailed) {
        if (state.changeFailed) {
            onChangeFailureShown()
            snackbars.showSnackbar(refused)
        }
    }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.conversations_back))
                    }
                },
                actions = {
                    if (state.conversations.any { it.unread }) {
                        IconButton(onClick = actions.onReadAll) {
                            Icon(AlohaIcons.Check, stringResource(R.string.conversations_read_all))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onNew,
                icon = { Icon(AlohaIcons.Compose, contentDescription = null) },
                text = { Text(stringResource(R.string.conversations_new)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.conversations.isEmpty() && state.loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.conversations.isEmpty() -> Text(
                    stringResource(if (state.failed) R.string.conversations_failed else R.string.conversations_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(AlohaSpacing.l),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        ConversationRow(conversation, now, actions.onOpen) { deleting = conversation.id }
                    }
                    if (!state.done) {
                        item(key = "more") {
                            TextButton(
                                onClick = actions.onMore,
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.s),
                            ) { Text(stringResource(R.string.conversations_more)) }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.conversations_delete_title)) },
            text = { Text(stringResource(R.string.conversations_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    actions.onDelete(id)
                }) { Text(stringResource(R.string.conversations_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** Who with, the last message (its content warning while it has one), when, and whether it is unread. */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    now: Instant,
    onOpen: (Conversation) -> Unit,
    onDelete: () -> Unit,
) {
    val last = conversation.lastStatus
    val names = conversation.accounts.joinToString(", ") { it.bestDisplayName }
        .ifEmpty { stringResource(R.string.conversations_only_you) }
    val excerpt = remember(last) {
        last?.let { if (it.hasContentWarning) it.spoilerText else StatusHtmlParser.plainText(it.content) }.orEmpty()
    }
    val unread = stringResource(R.string.conversations_unread)
    val weight = if (conversation.unread) FontWeight.Bold else null
    ListItem(
        modifier = Modifier.clickable { onOpen(conversation) }
            .semantics { if (conversation.unread) stateDescription = unread },
        leadingContent = { Avatar(conversation.accounts.firstOrNull()?.avatar, AVATAR) },
        overlineContent = last?.let {
            { PostTime(it.createdAt, now, LocalTextStyle.current, LocalContentColor.current) }
        },
        headlineContent = { Text(names, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(excerpt, fontWeight = weight, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(AlohaIcons.Delete, stringResource(R.string.conversations_delete, names))
            }
        },
    )
}

private val AVATAR = 48.dp
