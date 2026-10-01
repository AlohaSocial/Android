// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import social.aloha.core.data.photos.Albums
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AlbumsKey
import social.aloha.core.ui.ConfirmDialog

@Immutable
internal data class AlbumsUiState(
    val albums: List<MediaCollection> = emptyList(),
    /** The reader's own albums, which can be made, renamed and deleted. */
    val own: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** A change the server did not take; said once. */
    val changeFailed: Boolean = false,
)

/** An account's albums, or the reader's own, which the reader makes, renames and deletes. */
@HiltViewModel(assistedFactory = AlbumsViewModel.Factory::class)
internal class AlbumsViewModel @AssistedInject constructor(
    @Assisted private val key: AlbumsKey,
    private val accounts: AccountRepository,
    private val albums: Albums,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: AlbumsKey): AlbumsViewModel
    }

    private val state = MutableStateFlow(AlbumsUiState())
    val uiState: StateFlow<AlbumsUiState> = state.asStateFlow()

    init {
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val reader = reader()
            val own = reader != null && (key.ownerId == null || key.ownerId == reader.serverAccountId)
            val answer = reader?.let { if (own) albums.own(it) else albums.of(it, key.ownerId.orEmpty()) }
            state.update {
                if (answer is Answer.Got) {
                    it.copy(albums = answer.value, own = own, loading = false)
                } else {
                    it.copy(own = own, loading = false, failed = true)
                }
            }
        }
    }

    fun onCreate(title: String, description: String) = change { reader ->
        (albums.create(reader, title, description) as? Answer.Got)?.value?.let { listOf(it) + current }
    }

    fun onRename(album: MediaCollection, title: String) = change { reader ->
        (albums.rename(reader, album, title) as? Answer.Got)?.value?.let { renamed ->
            current.map { if (it.id == album.id) renamed else it }
        }
    }

    fun onDelete(id: String) = change { reader ->
        if (albums.delete(reader, id) is Answer.Got) current.filterNot { it.id == id } else null
    }

    fun onChangeFailedShown() = state.update { it.copy(changeFailed = false) }

    private val current get() = state.value.albums

    /** Makes a change as the reader; the albums it answers with, or null when the server would not. */
    private fun change(made: suspend (SignedInAccount) -> List<MediaCollection>?) {
        viewModelScope.launch {
            val list = reader()?.let { made(it) }
            state.update { if (list != null) it.copy(albums = list) else it.copy(changeFailed = true) }
        }
    }

    private suspend fun reader() = accounts.byId(key.readerId)
}

@Composable
public fun AlbumsRoute(
    key: AlbumsKey,
    onOpen: (album: MediaCollection, own: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<AlbumsViewModel, AlbumsViewModel.Factory>(key = key.toString()) { it.create(key) }
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
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    AlbumsScreen(
        state,
        AlbumsScreenActions(
            onOpen = { onOpen(it, state.own) },
            onCreate = { creating = true },
            onRename = { renaming = it.id },
            onDelete = { deleting = it.id },
            onRetry = viewModel::onRetry,
            onBack = onBack,
        ),
        snackbars,
        modifier,
    )
    if (creating) {
        AlbumNameDialog(
            title = stringResource(R.string.albums_new),
            initial = "",
            askDescription = true,
            confirm = stringResource(R.string.albums_create),
            onDismiss = { creating = false },
        ) { title, description -> viewModel.onCreate(title, description) }
    }
    renaming?.let { id ->
        state.albums.firstOrNull { it.id == id }?.let { album ->
            AlbumNameDialog(
                title = stringResource(R.string.albums_rename),
                initial = album.title,
                askDescription = false,
                confirm = stringResource(R.string.albums_rename_confirm),
                onDismiss = { renaming = null },
            ) { title, _ -> viewModel.onRename(album, title) }
        }
    }
    deleting?.let { id ->
        val name = state.albums.firstOrNull { it.id == id }?.title.orEmpty()
        ConfirmDialog(
            stringResource(R.string.albums_delete_title, name),
            stringResource(R.string.albums_delete_body),
            stringResource(R.string.albums_delete),
            onDismiss = { deleting = null },
        ) { viewModel.onDelete(id) }
    }
}

/** What the albums screen can ask for. */
internal class AlbumsScreenActions(
    val onOpen: (MediaCollection) -> Unit,
    val onCreate: () -> Unit,
    val onRename: (MediaCollection) -> Unit,
    val onDelete: (MediaCollection) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumsScreen(
    state: AlbumsUiState,
    actions: AlbumsScreenActions,
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.albums_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(AlohaIcons.Back, stringResource(R.string.photos_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (state.own) {
                ExtendedFloatingActionButton(
                    onClick = actions.onCreate,
                    icon = { Icon(AlohaIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.albums_new)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(state.albums, key = { it.id }) { album ->
                AlbumRow(album, state.own, actions)
                HorizontalDivider()
            }
            item(key = "footer") {
                ListFooter(
                    loading = state.loading,
                    failed = state.failed,
                    empty = state.albums.isEmpty(),
                    emptyText = stringResource(if (state.own) R.string.albums_none_own else R.string.albums_none),
                    onRetry = actions.onRetry,
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(album: MediaCollection, own: Boolean, actions: AlbumsScreenActions) {
    ListItem(
        headlineContent = { Text(album.title.ifBlank { stringResource(R.string.albums_untitled) }) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.albums_posts, album.postCount, album.postCount))
        },
        leadingContent = {
            // an album's cover over its icon, which stays where there is no cover yet
            Box(
                Modifier.size(THUMBNAIL).clip(RoundedCornerShape(CORNER))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AlohaIcons.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                AsyncImage(
                    model = album.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        },
        trailingContent = if (own) {
            { AlbumMenu(album, actions) }
        } else {
            null
        },
        modifier = Modifier.clickable { actions.onOpen(album) },
    )
}

@Composable
private fun AlbumMenu(album: MediaCollection, actions: AlbumsScreenActions) {
    var open by remember { mutableStateOf(false) }
    // which album the button is for, said aloud: every row has one
    Box {
        IconButton(onClick = { open = true }) {
            Icon(AlohaIcons.More, stringResource(R.string.albums_options, album.title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.albums_rename)) },
                onClick = {
                    open = false
                    actions.onRename(album)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.albums_delete)) },
                onClick = {
                    open = false
                    actions.onDelete(album)
                },
            )
        }
    }
}

private val THUMBNAIL = 56.dp
private val CORNER = 8.dp
