// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import social.aloha.core.data.photos.Albums
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.Status
import social.aloha.core.navigation.AlbumKey

@Immutable
internal data class AlbumUiState(
    val posts: List<Status> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** A removal the server did not take; said once. */
    val changeFailed: Boolean = false,
)

/** One album's posts, each a square of its first picture; the reader takes their own out of it. */
@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
internal class AlbumViewModel @AssistedInject constructor(
    @Assisted private val key: AlbumKey,
    private val accounts: AccountRepository,
    private val albums: Albums,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: AlbumKey): AlbumViewModel
    }

    private val state = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = state.asStateFlow()

    init {
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val answer = accounts.byId(key.readerId)?.let { albums.posts(it, key.albumId) }
            state.update {
                if (answer is Answer.Got) {
                    it.copy(posts = answer.value, loading = false)
                } else {
                    it.copy(loading = false, failed = true)
                }
            }
        }
    }

    fun onRemove(statusId: String) {
        viewModelScope.launch {
            val removed = accounts.byId(key.readerId)?.let { albums.remove(it, key.albumId, statusId) }
            state.update { now ->
                if (removed is Answer.Got) {
                    now.copy(posts = now.posts.filterNot { it.id == statusId })
                } else {
                    now.copy(changeFailed = true)
                }
            }
        }
    }

    fun onChangeFailedShown() = state.update { it.copy(changeFailed = false) }
}

@Composable
public fun AlbumRoute(
    key: AlbumKey,
    onOpen: (statusId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val failed = stringResource(R.string.albums_change_failed)
    LaunchedEffect(state.changeFailed) {
        if (state.changeFailed) {
            viewModel.onChangeFailedShown()
            snackbars.showSnackbar(failed)
        }
    }
    AlbumScreen(
        state,
        key.title,
        onOpen,
        onRemove = viewModel::onRemove.takeIf { key.own },
        onRetry = viewModel::onRetry,
        onBack = onBack,
        snackbars = snackbars,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumScreen(
    state: AlbumUiState,
    title: String,
    onOpen: (String) -> Unit,
    onRemove: ((String) -> Unit)?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
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
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(CELL), Modifier.padding(padding).fillMaxSize()) {
            items(state.posts, key = { it.id }) { post -> PhotoSquare(post, onOpen, onRemove) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                ListFooter(
                    loading = state.loading,
                    failed = state.failed,
                    empty = state.posts.isEmpty(),
                    emptyText = stringResource(R.string.album_empty),
                    onRetry = onRetry,
                )
            }
        }
    }
}
