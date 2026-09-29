// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Reaction
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.navigation.StatusListKey
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.rememberEmojiContent

@Composable
public fun StatusListRoute(key: StatusListKey, navigation: ThreadNavigation, modifier: Modifier = Modifier) {
    val viewModel =
        hiltViewModel<StatusListViewModel, StatusListViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val context = LocalContext.current
    val nav by rememberUpdatedState(navigation)
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    // quotes are read here, not acted on: acting on one opens its thread
    val rowActions = remember(context) {
        object : RoutedStatusActions(context, navigation = {
            nav
        }, onCopied = {}, onDeleteAsked = { nav.openThread(it.statusId) }) {
            override fun onBoost(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onFavourite(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onBookmark(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onMute(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onPin(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onVote(row: StatusRowUi, choices: List<Int>) = nav.openThread(row.statusId)
        }
    }
    StatusListScreen(key.kind, state, rowActions, onBack = nav::back, onRetry = { viewModel.onRetry(colors) }, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusListScreen(
    kind: StatusListKind,
    state: StatusListState,
    rowActions: StatusActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        when (kind) {
            StatusListKind.FavouritedBy -> R.string.thread_list_favourited_by
            StatusListKind.BoostedBy -> R.string.thread_list_boosted_by
            StatusListKind.Quotes -> R.string.thread_list_quotes
            StatusListKind.Reactions -> R.string.thread_list_reactions
        },
    )
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.thread_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                StatusListState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is StatusListState.Failed -> Failed(onRetry)

                is StatusListState.Accounts -> Listed(state.people.isEmpty()) {
                    items(state.people, key = { it.author.id }) { person ->
                        PersonRow(person) { rowActions.onProfile(person.author.id) }
                        HorizontalDivider()
                    }
                }

                is StatusListState.Posts -> Listed(state.rows.isEmpty()) {
                    items(state.rows, key = { it.rowId }) { row ->
                        StatusCard(row, state.now, SensitiveMediaPolicy.Blur, rowActions, showActions = false)
                        HorizontalDivider()
                    }
                }

                is StatusListState.Reactions -> Listed(state.reactions.isEmpty()) {
                    items(state.reactions, key = { it.name }) { reaction ->
                        ReactionRow(reaction)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Listed(empty: Boolean, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    if (empty) {
        Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.thread_list_empty), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.thread_list_error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text(stringResource(R.string.thread_retry)) }
    }
}

@Composable
private fun PersonRow(person: StatusListState.Person, onOpen: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Avatar(person.author.avatarUrl, AVATAR) },
        headlineContent = {
            Text(
                person.author.name,
                inlineContent = rememberEmojiContent(person.emojis, animate = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(person.author.handle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** A custom emoji draws as its image, a Unicode one as itself; either reads as its name and count. */
@Composable
private fun ReactionRow(reaction: Reaction) {
    val label = pluralStringResource(R.plurals.thread_reaction_count, reaction.count, reaction.name, reaction.count)
    ListItem(
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        leadingContent = {
            val url = reaction.staticUrl ?: reaction.url
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(EMOJI))
            } else {
                Text(reaction.name, style = MaterialTheme.typography.headlineSmall)
            }
        },
        headlineContent = { Text(reaction.count.toString(), style = MaterialTheme.typography.titleMedium) },
    )
}

private val AVATAR = 40.dp
private val EMOJI = 32.dp
