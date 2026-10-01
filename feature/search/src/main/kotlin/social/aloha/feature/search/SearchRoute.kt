// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.navigation.SearchKey
import social.aloha.core.ui.AccountRow
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.rememberThreadRoutedActions

/**
 * Search: accounts, hashtags and posts on the reader's server, from anywhere by their address. A post
 * found opens its thread, where it is acted on; a pasted address that names one post or person opens it.
 */
@Composable
public fun SearchRoute(
    key: SearchKey,
    navigation: StatusNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    explore: @Composable () -> Unit = {},
) {
    val viewModel = hiltViewModel<SearchViewModel, SearchViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val nav by rememberUpdatedState(navigation)
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    LaunchedEffect(state.found) {
        when (val found = state.found) {
            is Found.Post -> nav.openThread(found.statusId)
            is Found.Person -> nav.openProfile(found.accountId, null)
            null -> return@LaunchedEffect
        }
        viewModel.onFoundShown()
    }
    // a post found is read here and acted on in its thread
    val rowActions = rememberThreadRoutedActions(navigation)
    SearchScreen(
        state,
        SearchActions(viewModel::onQuery, viewModel::onSubmit, viewModel::onClearRecent, onBack),
        rowActions,
        modifier,
        explore = explore,
    )
}

/** What the search screen asks for: typing, a submit, clearing the recent searches, and leaving. */
internal class SearchActions(
    val onQuery: (String) -> Unit,
    val onSubmit: (String) -> Unit,
    val onClearRecent: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    actions: SearchActions,
    rowActions: StatusActions,
    modifier: Modifier = Modifier,
    now: Instant = remember { Instant.now() },
    explore: @Composable () -> Unit = {},
) {
    val title = stringResource(R.string.search_title)
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = state.query,
                onQueryChange = actions.onQuery,
                onSearch = actions.onSubmit,
                expanded = true,
                onExpandedChange = { if (!it) actions.onBack() },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    IconButton(onClick = actions.onBack) { Icon(AlohaIcons.Back, stringResource(R.string.search_back)) }
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQuery("") }) {
                            Icon(AlohaIcons.Close, stringResource(R.string.search_clear))
                        }
                    }
                },
            )
        },
        expanded = true,
        onExpandedChange = { if (!it) actions.onBack() },
        modifier = modifier.semantics { paneTitle = title },
        // the rows below are drawn on the surface, so the sheet is too
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        val trouble = state.trouble
        when {
            // nothing typed: the recent searches, and what is going on
            state.query.isBlank() -> Column {
                Recent(state.recent, actions)
                explore()
            }

            state.searched && state.empty -> Message(stringResource(R.string.search_nothing, state.query.trim()))

            state.searched -> Results(state, rowActions, now)

            trouble != null -> Trouble(trouble)

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

/** The recent searches as a row of chips, each searched again on a tap; nothing when there are none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Recent(recent: List<String>, actions: SearchActions) {
    if (recent.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(start = AlohaSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.search_recent),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = actions.onClearRecent) { Text(stringResource(R.string.search_recent_clear)) }
    }
    FlowRow(
        Modifier.padding(horizontal = AlohaSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        recent.forEach { query ->
            AssistChip(
                onClick = { actions.onSubmit(query) },
                label = { Text(query, maxLines = 1) },
                leadingIcon = { Icon(AlohaIcons.Recent, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun Results(state: SearchUiState, rowActions: StatusActions, now: Instant) {
    LazyColumn(Modifier.fillMaxSize()) {
        section(R.string.search_accounts, state.accounts.isNotEmpty()) {
            items(state.accounts, key = { "a:${it.author.id}" }) { person ->
                AccountRow(person.author, person.emojis, onOpen = { rowActions.onProfile(person.author.id) })
            }
        }
        section(R.string.search_hashtags, state.hashtags.isNotEmpty()) {
            items(state.hashtags, key = { "t:${it.name}" }) { tag ->
                ListItem(
                    leadingContent = { Icon(AlohaIcons.Hashtag, contentDescription = null) },
                    headlineContent = { Text("#${tag.name}") },
                    modifier = Modifier.clickable { rowActions.onLink(RichLinkTarget.Hashtag(tag.name)) },
                )
            }
        }
        section(R.string.search_posts, state.posts.isNotEmpty()) {
            items(state.posts, key = { "p:${it.rowId}" }) { row ->
                StatusCard(row, now, SensitiveMediaPolicy.Blur, rowActions, showActions = false)
                HorizontalDivider()
            }
        }
    }
}

/** A heading and what is under it, where there is anything. */
private fun LazyListScope.section(heading: Int, shown: Boolean, content: LazyListScope.() -> Unit) {
    if (!shown) return
    item(key = "h:$heading") {
        Text(
            stringResource(heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)
                .semantics { heading() },
        )
    }
    content()
}

@Composable
private fun Trouble(trouble: Trouble) {
    TroubleStrip(stringResource(if (trouble == Trouble.Offline) R.string.search_offline else R.string.search_error))
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
