// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.navigation.SavedKind
import social.aloha.core.navigation.SavedPostsKey
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.rememberThreadRoutedActions

/**
 * The reader's bookmarked, favourited or archived posts; a post opens its thread, where it is acted
 * on. An archived one is put back on the profile from here.
 */
@Composable
public fun SavedPostsRoute(
    key: SavedPostsKey,
    navigation: StatusNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val mapper = remember(colors) { viewModel.mapper(colors) }
    val rowActions = rememberThreadRoutedActions(navigation)
    val actions = SavedActions(
        onMore = viewModel::onMore,
        onUnarchive = viewModel::onUnarchive.takeIf { key.kind == SavedKind.Archived },
        onUnarchiveFailureShown = viewModel::onUnarchiveFailureShown,
        onBack = onBack,
    )
    SavedPostsScreen(key.kind, state, mapper, rowActions, actions, modifier)
}

/** What the saved posts screen asks for; [onUnarchive] only for the archive. */
internal class SavedActions(
    val onMore: () -> Unit,
    val onUnarchive: ((String) -> Unit)?,
    val onUnarchiveFailureShown: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedPostsScreen(
    kind: SavedKind,
    state: SavedPostsUiState,
    mapper: StatusRowMapper,
    rowActions: StatusActions,
    actions: SavedActions,
    modifier: Modifier = Modifier,
    now: Instant = remember { Instant.now() },
) {
    val rows = remember(state.posts, mapper) { state.posts.map { mapper.map(it, state.viewer) } }
    val title = stringResource(kind.title)
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.saved_unarchive_failed)
    LaunchedEffect(state.unarchiveFailed) {
        if (state.unarchiveFailed) {
            actions.onUnarchiveFailureShown()
            snackbars.showSnackbar(refused)
        }
    }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(AlohaIcons.Back, stringResource(R.string.saved_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                rows.isEmpty() && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                rows.isEmpty() -> Text(
                    stringResource(if (state.failed) R.string.saved_failed else kind.empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(AlohaSpacing.l),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.rowId }) { row ->
                        StatusCard(row, now, SensitiveMediaPolicy.Blur, rowActions, showActions = false)
                        actions.onUnarchive?.let { unarchive ->
                            // whose post, said aloud: every row has one
                            val label = stringResource(R.string.saved_unarchive_post, row.author.plainName)
                            TextButton(
                                onClick = { unarchive(row.statusId) },
                                modifier = Modifier.padding(horizontal = AlohaSpacing.s)
                                    .semantics { contentDescription = label },
                            ) { Text(stringResource(R.string.saved_unarchive)) }
                        }
                        HorizontalDivider()
                    }
                    if (!state.done) {
                        item(key = "more") {
                            TextButton(
                                onClick = actions.onMore,
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.s),
                            ) { Text(stringResource(R.string.saved_more)) }
                        }
                    }
                }
            }
        }
    }
}

private val SavedKind.title: Int
    get() = when (this) {
        SavedKind.Bookmarks -> R.string.saved_bookmarks
        SavedKind.Favourites -> R.string.saved_favourites
        SavedKind.Archived -> R.string.saved_archived
    }

private val SavedKind.empty: Int
    get() = when (this) {
        SavedKind.Bookmarks -> R.string.saved_bookmarks_none
        SavedKind.Favourites -> R.string.saved_favourites_none
        SavedKind.Archived -> R.string.saved_archived_none
    }
