// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.hashtags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.data.tags.TagGroup
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.navigation.HashtagsKey

/**
 * The hashtags the reader follows, each opening its timeline and unfollowed with a tap, a name typed to
 * follow another, and the reader's tag groups: several hashtags read as one timeline, kept here.
 */
@Composable
public fun HashtagsRoute(
    key: HashtagsKey,
    onTag: (String) -> Unit,
    onGroup: (TagGroup) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<HashtagsViewModel, HashtagsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        HashtagsActions(
            onTag = onTag,
            onGroup = onGroup,
            onFollow = viewModel::onFollow,
            onSaveGroup = viewModel::onSaveGroup,
            onDeleteGroup = viewModel::onDeleteGroup,
            onBack = onBack,
        )
    }
    HashtagsScreen(state, actions, viewModel::onNoticeShown, viewModel::onRetry, modifier)
}

/** What the hashtags screen asks for. */
internal class HashtagsActions(
    val onTag: (String) -> Unit,
    val onGroup: (TagGroup) -> Unit,
    /** Follows a hashtag, or unfollows it unless [follow]. */
    val onFollow: (name: String, follow: Boolean) -> Unit,
    val onSaveGroup: (name: String, tags: List<String>, previous: String?) -> Unit,
    val onDeleteGroup: (String) -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HashtagsScreen(
    state: HashtagsUiState,
    actions: HashtagsActions,
    onNoticeShown: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.hashtags_title)
    val snackbars = remember { SnackbarHostState() }
    val notice = state.notice?.let { stringResource(it.text) }
    LaunchedEffect(state.notice) {
        if (notice != null) {
            onNoticeShown()
            snackbars.showSnackbar(notice)
        }
    }
    // the group being edited, or a new one ("") being made
    var editing by remember { mutableStateOf<TagGroup?>(null) }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.hashtags_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "follow") { FollowField { actions.onFollow(it, true) } }
            heading(R.string.hashtags_followed)
            if (state.followed.isEmpty() && !state.loading) {
                item(key = "none") {
                    Note(stringResource(if (state.failed) R.string.hashtags_failed else R.string.hashtags_none))
                    if (state.failed) {
                        TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = AlohaSpacing.s)) {
                            Text(stringResource(R.string.hashtags_retry))
                        }
                    }
                }
            }
            items(state.followed, key = { "t:${it.name}" }) { tag ->
                ListItem(
                    leadingContent = { Icon(AlohaIcons.Hashtag, contentDescription = null) },
                    headlineContent = { Text("#${tag.name}") },
                    trailingContent = {
                        // which tag, said aloud: every row has one
                        val unfollow = stringResource(R.string.hashtags_unfollow_tag, tag.name)
                        TextButton(
                            onClick = { actions.onFollow(tag.name, false) },
                            modifier = Modifier.semantics { contentDescription = unfollow },
                        ) { Text(stringResource(R.string.hashtags_unfollow)) }
                    },
                    modifier = Modifier.clickable { actions.onTag(tag.name) },
                )
            }
            heading(R.string.hashtags_groups)
            item(key = "groups-note") { Note(stringResource(R.string.hashtags_groups_note)) }
            items(state.groups, key = { "g:${it.name}" }) { group ->
                ListItem(
                    leadingContent = { Icon(AlohaIcons.Hashtag, contentDescription = null) },
                    headlineContent = { Text(group.name) },
                    supportingContent = { Text(group.tags.joinToString(" ") { "#$it" }) },
                    trailingContent = {
                        IconButton(onClick = { editing = group }) {
                            Icon(AlohaIcons.Edited, stringResource(R.string.hashtags_group_edit, group.name))
                        }
                    },
                    modifier = Modifier.clickable { actions.onGroup(group) },
                )
            }
            item(key = "new-group") {
                TextButton(onClick = {
                    editing = TagGroup("", emptyList())
                }, modifier = Modifier.padding(AlohaSpacing.s)) {
                    Text(stringResource(R.string.hashtags_group_new))
                }
            }
        }
    }
    editing?.let { group ->
        GroupDialog(
            group,
            onDismiss = { editing = null },
            onSave = { name, tags ->
                editing = null
                actions.onSaveGroup(name, tags, group.name.takeIf { it.isNotEmpty() })
            },
            onDelete = if (group.name.isEmpty()) {
                null
            } else {
                {
                    editing = null
                    actions.onDeleteGroup(group.name)
                }
            },
        )
    }
}

@Composable
private fun FollowField(onFollow: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(AlohaSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.hashtags_follow_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                onFollow(name)
                name = ""
            },
            enabled = name.isNotBlank(),
        ) { Text(stringResource(R.string.hashtags_follow)) }
    }
}

/** A group's name and its hashtags, written apart by spaces or commas; [onDelete] where it exists. */
@Composable
private fun GroupDialog(
    group: TagGroup,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(group.name) }
    var tags by rememberSaveable { mutableStateOf(group.tags.joinToString(" ") { "#$it" }) }
    val parsed = groupTags(tags)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (onDelete ==
                    null
                ) {
                    stringResource(R.string.hashtags_group_new)
                } else {
                    stringResource(R.string.hashtags_group_edit, group.name)
                },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hashtags_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.hashtags_group_tags)) },
                    modifier = Modifier.fillMaxWidth().padding(top = AlohaSpacing.s),
                )
                onDelete?.let {
                    TextButton(onClick = it, modifier = Modifier.padding(top = AlohaSpacing.s)) {
                        Text(stringResource(R.string.hashtags_group_delete))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, parsed) }, enabled = name.isNotBlank() && parsed.isNotEmpty()) {
                Text(stringResource(R.string.hashtags_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private val HashtagsNotice.text: Int
    get() = when (this) {
        HashtagsNotice.NotATag -> R.string.hashtags_not_a_tag
        HashtagsNotice.FollowFailed -> R.string.hashtags_follow_failed
        HashtagsNotice.NameTaken -> R.string.hashtags_group_taken
    }

/** The hashtags typed for a group, apart by spaces, commas or lines. */
internal fun groupTags(typed: String): List<String> = typed.split(' ', ',', '\n').filter { it.isNotBlank() }

private fun LazyListScope.heading(text: Int) {
    item(key = "h:$text") {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)
                .semantics { heading() },
        )
    }
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
