// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.photos.PhotoDiscovery
import social.aloha.core.data.photos.PhotoExplore
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Account
import social.aloha.core.model.Status
import social.aloha.core.navigation.PhotoExploreKey
import social.aloha.core.ui.StatusNavigation

@Immutable
internal data class PhotoExploreUiState(
    val explore: PhotoExplore = PhotoExplore(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/** Photos' Explore: trending pictures here and across the network, trending hashtags, popular people. */
@HiltViewModel(assistedFactory = PhotoExploreViewModel.Factory::class)
internal class PhotoExploreViewModel @AssistedInject constructor(
    @Assisted private val key: PhotoExploreKey,
    private val accounts: AccountRepository,
    private val discovery: PhotoDiscovery,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: PhotoExploreKey): PhotoExploreViewModel
    }

    private val state = MutableStateFlow(PhotoExploreUiState())
    val uiState: StateFlow<PhotoExploreUiState> = state.asStateFlow()

    init {
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val answer = accounts.byId(key.readerId)?.let { discovery.explore(it) }
            state.update {
                if (answer is Answer.Got) {
                    it.copy(explore = answer.value, loading = false)
                } else {
                    it.copy(loading = false, failed = true)
                }
            }
        }
    }
}

@Composable
public fun PhotoExploreRoute(
    key: PhotoExploreKey,
    navigation: StatusNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<PhotoExploreViewModel, PhotoExploreViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PhotoExploreScreen(
        state,
        onPost = navigation::openThread,
        onTag = navigation::openTag,
        onPerson = { navigation.openProfile(it.id, null) },
        onRetry = viewModel::onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoExploreScreen(
    state: PhotoExploreUiState,
    onPost: (String) -> Unit,
    onTag: (String) -> Unit,
    onPerson: (Account) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.explore_title)
    val explore = state.explore
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.photos_back)) }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(CELL), Modifier.padding(padding).fillMaxSize()) {
            photos(R.string.explore_trending, explore.trending, onPost)
            if (explore.tags.isNotEmpty()) {
                heading(R.string.explore_tags)
                item(span = { GridItemSpan(maxLineSpan) }, key = "tags") { Tags(explore.tags.map { it.name }, onTag) }
            }
            if (explore.people.isNotEmpty()) {
                heading(R.string.photos_people)
                items(explore.people, key = { "person:${it.id}" }, span = { GridItemSpan(maxLineSpan) }) {
                    Person(it, onPerson)
                }
            }
            photos(R.string.explore_network, explore.network, onPost)
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                ListFooter(
                    loading = state.loading,
                    failed = state.failed,
                    empty = explore.isEmpty,
                    emptyText = stringResource(R.string.explore_empty),
                    onRetry = onRetry,
                )
            }
        }
    }
}

/** A section of squares under its heading; none at all where the server had nothing. */
private fun LazyGridScope.photos(title: Int, posts: List<Status>, onPost: (String) -> Unit) {
    if (posts.isEmpty()) return
    heading(title)
    items(posts, key = { "$title:${it.id}" }) { PhotoSquare(it, onPost, onRemove = null) }
}

private fun LazyGridScope.heading(title: Int) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "heading:$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m).semantics { heading() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(names: List<String>, onTag: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        names.forEach { name -> AssistChip(onClick = { onTag(name) }, label = { Text("#$name") }) }
    }
}

@Composable
private fun Person(account: Account, onPerson: (Account) -> Unit) {
    ListItem(
        headlineContent = { Text(account.bestDisplayName) },
        supportingContent = { Text("@${account.acct}") },
        leadingContent = {
            AsyncImage(
                model = account.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(
                    AVATAR,
                ).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        },
        modifier = Modifier.clickable { onPerson(account) },
    )
}

private val AVATAR = 40.dp
