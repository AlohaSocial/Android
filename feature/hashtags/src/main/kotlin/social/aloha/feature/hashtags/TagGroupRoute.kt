// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.hashtags

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.navigation.TagGroupKey
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/** A tag group as one timeline: its hashtags' posts merged, newest first; a post opens its thread. */
@Composable
public fun TagGroupRoute(
    key: TagGroupKey,
    navigation: StatusNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<TagGroupViewModel, TagGroupViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val mapper = remember(colors) { viewModel.mapper(colors) }
    val context = LocalContext.current
    val nav by rememberUpdatedState(navigation)
    // a post is read here and acted on in its thread
    val rowActions = remember(context) {
        object : RoutedStatusActions(context, navigation = { nav }, onCopied = {}, onDeleteAsked = {
            nav.openThread(it.row.statusId)
        }) {
            override fun onBoost(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onFavourite(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onBookmark(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onMute(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onPin(row: StatusRowUi) = nav.openThread(row.statusId)

            override fun onVote(row: StatusRowUi, choices: List<Int>) = nav.openThread(row.statusId)
        }
    }
    TagGroupScreen(key.name, state, mapper, rowActions, viewModel::onMore, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagGroupScreen(
    name: String,
    state: TagGroupUiState,
    mapper: StatusRowMapper,
    rowActions: StatusActions,
    onMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.posts, mapper) { state.posts.map { mapper.map(it, state.viewer) } }
    val now = remember { Instant.now() }
    Scaffold(
        modifier = modifier.semantics { paneTitle = name },
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.hashtags_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                rows.isEmpty() && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                rows.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(AlohaSpacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(if (state.failed) R.string.hashtags_failed else R.string.hashtags_group_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // what failed is the next page, which is what asking for more asks again
                    if (state.failed) TextButton(onClick = onMore) { Text(stringResource(R.string.hashtags_retry)) }
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.rowId }) { row ->
                        StatusCard(row, now, SensitiveMediaPolicy.Blur, rowActions, showActions = false)
                        HorizontalDivider()
                    }
                    if (!state.done) {
                        item(key = "more") {
                            TextButton(
                                onClick = onMore,
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.s),
                            ) { Text(stringResource(R.string.hashtags_more)) }
                        }
                    }
                }
            }
        }
    }
}
