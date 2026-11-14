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
import social.aloha.core.data.photos.Collections
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.MediaCollection
import social.aloha.core.navigation.AddToCollectionKey

@Immutable
internal data class AddToCollectionUiState(
    val collections: List<MediaCollection> = emptyList(),
    /** The collections the post went into here. */
    val added: Set<String> = emptySet(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val changeFailed: Boolean = false,
)

/** The reader's collections, to put one of their posts into: a collection picked, or one made for it. */
@HiltViewModel(assistedFactory = AddToCollectionViewModel.Factory::class)
internal class AddToCollectionViewModel @AssistedInject constructor(
    @Assisted private val key: AddToCollectionKey,
    private val accounts: AccountRepository,
    private val collections: Collections,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: AddToCollectionKey): AddToCollectionViewModel
    }

    private val state = MutableStateFlow(AddToCollectionUiState())
    val uiState: StateFlow<AddToCollectionUiState> = state.asStateFlow()

    init {
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val answer = accounts.byId(key.readerId)?.let { collections.own(it) }
            state.update {
                if (answer is Answer.Got) {
                    it.copy(collections = answer.value, loading = false)
                } else {
                    it.copy(loading = false, failed = true)
                }
            }
        }
    }

    fun onAdd(collection: MediaCollection) {
        if (collection.id in state.value.added) return
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId) ?: return@launch
            val added = collections.add(reader, collection.id, key.statusId) is Answer.Got
            state.update { if (added) it.copy(added = it.added + collection.id) else it.copy(changeFailed = true) }
        }
    }

    /** A new collection with the post already in it. */
    fun onCreate(title: String, description: String) {
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId) ?: return@launch
            val made = (collections.create(reader, title, description) as? Answer.Got)?.value
            val added = made != null && collections.add(reader, made.id, key.statusId) is Answer.Got
            state.update {
                when {
                    made == null -> it.copy(changeFailed = true)
                    added -> it.copy(collections = listOf(made) + it.collections, added = it.added + made.id)
                    else -> it.copy(collections = listOf(made) + it.collections, changeFailed = true)
                }
            }
        }
    }

    fun onChangeFailedShown() = state.update { it.copy(changeFailed = false) }
}

@Composable
public fun AddToCollectionRoute(key: AddToCollectionKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel =
        hiltViewModel<AddToCollectionViewModel, AddToCollectionViewModel.Factory>(key = key.toString()) {
            it.create(key)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val failed = stringResource(R.string.collections_change_failed)
    LaunchedEffect(state.changeFailed) {
        if (state.changeFailed) {
            snackbars.showSnackbar(failed)
            viewModel.onChangeFailedShown()
        }
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    AddToCollectionScreen(state, viewModel::onAdd, { creating = true }, viewModel::onRetry, onBack, snackbars, modifier)
    if (creating) {
        CollectionNameDialog(
            title = stringResource(R.string.collections_new),
            initial = "",
            askDescription = true,
            confirm = stringResource(R.string.collections_create),
            onDismiss = { creating = false },
            onConfirm = viewModel::onCreate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToCollectionScreen(
    state: AddToCollectionUiState,
    onAdd: (MediaCollection) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.add_to_collection_title)
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
                    headlineContent = { Text(stringResource(R.string.collections_new)) },
                    leadingContent = { Icon(AlohaIcons.Add, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onCreate),
                )
                HorizontalDivider()
            }
            items(state.collections, key = { it.id }) { collection ->
                val added = collection.id in state.added
                val addedText = stringResource(R.string.add_to_collection_added)
                ListItem(
                    headlineContent = {
                        Text(collection.title.ifBlank { stringResource(R.string.collections_untitled) })
                    },
                    supportingContent = {
                        val count = collection.postCount
                        Text(pluralStringResource(R.plurals.collections_posts, count, count))
                    },
                    trailingContent = if (added) {
                        { Icon(AlohaIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable(enabled = !added) { onAdd(collection) }
                        .semantics { if (added) stateDescription = addedText },
                )
                HorizontalDivider()
            }
            item(key = "footer") {
                ListFooter(
                    loading = state.loading,
                    failed = state.failed,
                    empty = state.collections.isEmpty(),
                    emptyText = stringResource(R.string.collections_none_own),
                    onRetry = onRetry,
                )
            }
        }
    }
}
