// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import social.aloha.core.model.MediaCollection
import social.aloha.core.navigation.AddToAlbumKey

@Immutable
internal data class AddToAlbumUiState(
    val albums: List<MediaCollection> = emptyList(),
    /** The albums the post went into here. */
    val added: Set<String> = emptySet(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val changeFailed: Boolean = false,
)

/** The reader's albums, to put one of their posts into: an album picked, or one made for it. */
@HiltViewModel(assistedFactory = AddToAlbumViewModel.Factory::class)
internal class AddToAlbumViewModel @AssistedInject constructor(
    @Assisted private val key: AddToAlbumKey,
    private val accounts: AccountRepository,
    private val albums: Albums,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: AddToAlbumKey): AddToAlbumViewModel
    }

    private val state = MutableStateFlow(AddToAlbumUiState())
    val uiState: StateFlow<AddToAlbumUiState> = state.asStateFlow()

    init {
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val answer = accounts.byId(key.readerId)?.let { albums.own(it) }
            state.update {
                if (answer is Answer.Got) {
                    it.copy(albums = answer.value, loading = false)
                } else {
                    it.copy(loading = false, failed = true)
                }
            }
        }
    }

    fun onAdd(album: MediaCollection) {
        if (album.id in state.value.added) return
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId) ?: return@launch
            val added = albums.add(reader, album.id, key.statusId) is Answer.Got
            state.update { if (added) it.copy(added = it.added + album.id) else it.copy(changeFailed = true) }
        }
    }

    /** A new album with the post already in it. */
    fun onCreate(title: String, description: String) {
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId) ?: return@launch
            val made = (albums.create(reader, title, description) as? Answer.Got)?.value
            val added = made != null && albums.add(reader, made.id, key.statusId) is Answer.Got
            state.update {
                when {
                    made == null -> it.copy(changeFailed = true)
                    added -> it.copy(albums = listOf(made) + it.albums, added = it.added + made.id)
                    else -> it.copy(albums = listOf(made) + it.albums, changeFailed = true)
                }
            }
        }
    }

    fun onChangeFailedShown() = state.update { it.copy(changeFailed = false) }
}

@Composable
public fun AddToAlbumRoute(key: AddToAlbumKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel =
        hiltViewModel<AddToAlbumViewModel, AddToAlbumViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val failed = stringResource(R.string.albums_change_failed)
    LaunchedEffect(state.changeFailed) {
        if (state.changeFailed) {
            viewModel.onChangeFailedShown()
            snackbars.showSnackbar(failed)
        }
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    AddToAlbumScreen(state, viewModel::onAdd, { creating = true }, viewModel::onRetry, onBack, snackbars, modifier)
    if (creating) {
        AlbumNameDialog(
            title = stringResource(R.string.albums_new),
            initial = "",
            askDescription = true,
            confirm = stringResource(R.string.albums_create),
            onDismiss = { creating = false },
            onConfirm = viewModel::onCreate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToAlbumScreen(
    state: AddToAlbumUiState,
    onAdd: (MediaCollection) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.add_to_album_title)
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
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "new") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.albums_new)) },
                    leadingContent = { Icon(AlohaIcons.Add, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onCreate),
                )
                HorizontalDivider()
            }
            items(state.albums, key = { it.id }) { album ->
                val added = album.id in state.added
                val addedText = stringResource(R.string.add_to_album_added)
                ListItem(
                    headlineContent = { Text(album.title.ifBlank { stringResource(R.string.albums_untitled) }) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.albums_posts, album.postCount, album.postCount))
                    },
                    trailingContent = if (added) {
                        { Icon(AlohaIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable(enabled = !added) { onAdd(album) }
                        .semantics { if (added) stateDescription = addedText },
                )
                HorizontalDivider()
            }
            item(key = "footer") {
                ListFooter(
                    loading = state.loading,
                    failed = state.failed,
                    empty = state.albums.isEmpty(),
                    emptyText = stringResource(R.string.albums_none_own),
                    onRetry = onRetry,
                )
            }
        }
    }
}
