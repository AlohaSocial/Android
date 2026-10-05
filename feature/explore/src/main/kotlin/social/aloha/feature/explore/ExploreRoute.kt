// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.network.endpoints.DirectoryOrder
import social.aloha.core.ui.EmptyState
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.Skeleton
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.TabPager
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.rememberThreadRoutedActions

/**
 * Explore, as [readerId]: what is trending (posts, hashtags, links), who to follow, and the directory
 * of the accounts here that chose to be in it. A post opens its thread, where it is acted on.
 */
@Composable
public fun ExploreRoute(readerId: String, navigation: StatusNavigation, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ExploreViewModel, ExploreViewModel.Factory>(key = "explore-$readerId") {
        it.create(readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val mapper = remember(colors) { viewModel.mapper(colors) }
    val nav by rememberUpdatedState(navigation)
    // a trending post is read here and acted on in its thread
    val rowActions = rememberThreadRoutedActions(navigation)
    val actions = remember(viewModel) {
        object : ExploreActions {
            override fun onTab(tab: ExploreTab) = viewModel.onTab(tab)

            override fun onRetry() = viewModel.onRetry()

            override fun onPeriod(period: String) = viewModel.onPeriod(period)

            override fun onOrder(order: DirectoryOrder) = viewModel.onOrder(order)

            override fun onMoreDirectory() = viewModel.moreDirectory()

            override fun onDismiss(accountId: String) = viewModel.onDismiss(accountId)

            override fun onFollowAll(slug: String) = viewModel.onFollowAll(slug)

            override fun onWeb(url: String) = nav.openWeb(url)
        }
    }
    ExploreScreen(state, mapper, actions, rowActions, modifier)
}

/** What Explore asks for. */
internal interface ExploreActions {
    fun onTab(tab: ExploreTab)

    fun onRetry()

    fun onPeriod(period: String)

    fun onOrder(order: DirectoryOrder)

    fun onMoreDirectory()

    fun onDismiss(accountId: String)

    fun onFollowAll(slug: String)

    fun onWeb(url: String)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExploreScreen(
    state: ExploreUiState,
    mapper: StatusRowMapper,
    actions: ExploreActions,
    rowActions: StatusActions,
    modifier: Modifier = Modifier,
) {
    TabPager(
        ExploreTab.entries.filter { state.trends || !it.trending },
        state.tab,
        actions::onTab,
        { stringResource(tabName(it)) },
        modifier.fillMaxSize(),
    ) { tab ->
        when (tab) {
            ExploreTab.Posts -> Loading(state.posts, actions::onRetry) {
                PostsTab(it, state.viewer, mapper, rowActions)
            }

            ExploreTab.Hashtags -> Loading(state.hashtags, actions::onRetry) {
                HashtagsTab(it, state, actions, rowActions)
            }

            ExploreTab.News -> Loading(state.news, actions::onRetry) { NewsTab(it, actions::onWeb) }

            ExploreTab.People -> Loading(state.people, actions::onRetry) {
                PeopleTab(it, state, mapper, actions, rowActions)
            }

            ExploreTab.Directory -> DirectoryTab(state.directory, mapper, actions, rowActions)
        }
    }
}

/** What [load] holds once it is there; a spinner until then, and why it is not, with a retry. */
@Composable
private fun <T> Loading(load: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (load) {
        Load.Waiting -> Skeleton()
        is Load.Failed -> Failed(load.trouble, onRetry)
        is Load.Loaded -> content(load.value)
    }
}

@Composable
internal fun Failed(trouble: Trouble, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(AlohaSpacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
        TroubleStrip(
            stringResource(if (trouble == Trouble.Offline) R.string.explore_offline else R.string.explore_error),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = AlohaSpacing.m)) {
            Text(stringResource(R.string.explore_retry))
        }
    }
}

@Composable
internal fun Empty(text: Int) = EmptyState(stringResource(text))

private fun tabName(tab: ExploreTab): Int = when (tab) {
    ExploreTab.Posts -> R.string.explore_posts
    ExploreTab.Hashtags -> R.string.explore_hashtags
    ExploreTab.News -> R.string.explore_news
    ExploreTab.People -> R.string.explore_people
    ExploreTab.Directory -> R.string.explore_directory
}
