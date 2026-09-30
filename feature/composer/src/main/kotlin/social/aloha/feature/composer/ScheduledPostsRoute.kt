// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.ScheduledPosts
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.ScheduledStatus
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.ScheduledPostsKey
import social.aloha.core.ui.ConfirmDialog
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.fullDate

@Immutable
internal data class ScheduledUiState(
    val posts: List<ScheduledStatus> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** A change the server did not take; said once. */
    val changeFailed: Boolean = false,
)

/** The posts waiting on the server for their time: moved to another time, or never sent. */
@HiltViewModel(assistedFactory = ScheduledPostsViewModel.Factory::class)
internal class ScheduledPostsViewModel @AssistedInject constructor(
    @Assisted private val key: ScheduledPostsKey,
    private val accounts: AccountRepository,
    private val scheduled: ScheduledPosts,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: ScheduledPostsKey): ScheduledPostsViewModel
    }

    private val state = MutableStateFlow(ScheduledUiState())
    val uiState: StateFlow<ScheduledUiState> = state.asStateFlow()

    init {
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val answer = reader()?.let { scheduled.all(it) }
            state.update {
                if (answer is Answer.Got) {
                    it.copy(posts = answer.value, loading = false)
                } else {
                    it.copy(loading = false, failed = true)
                }
            }
        }
    }

    fun onDelete(id: String) = change {
        if (scheduled.delete(it, id) is Answer.Got) posts.filterNot { post -> post.id == id } else null
    }

    fun onReschedule(id: String, at: Instant) = change {
        (scheduled.reschedule(it, id, at) as? Answer.Got)?.value?.let { moved ->
            posts.map { post -> if (post.id == id) moved else post }.sortedBy(ScheduledStatus::scheduledAt)
        }
    }

    fun onChangeFailedShown() = state.update { it.copy(changeFailed = false) }

    private val posts get() = state.value.posts

    /** Makes a change as the reader; the list it answers with, or null when the server would not. */
    private fun change(made: suspend (SignedInAccount) -> List<ScheduledStatus>?) {
        viewModelScope.launch {
            val list = reader()?.let { made(it) }
            state.update { if (list != null) it.copy(posts = list) else it.copy(changeFailed = true) }
        }
    }

    private suspend fun reader() = accounts.byId(key.readerId)
}

@Composable
public fun ScheduledPostsRoute(key: ScheduledPostsKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel =
        hiltViewModel<ScheduledPostsViewModel, ScheduledPostsViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val failed = stringResource(R.string.scheduled_change_failed)
    LaunchedEffect(state.changeFailed) {
        if (state.changeFailed) {
            viewModel.onChangeFailedShown()
            snackbars.showSnackbar(failed)
        }
    }
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    ScheduledPostsScreen(state, onBack, { moving = it }, { deleting = it }, viewModel::onRetry, snackbars, modifier)
    moving?.let { id ->
        ScheduleDialog(
            state.posts.firstOrNull { it.id == id }?.scheduledAt,
            now = Instant::now,
            onPick = {
                moving = null
                viewModel.onReschedule(id, it)
            },
            onDismiss = { moving = null },
        )
    }
    deleting?.let { id ->
        ConfirmDialog(
            stringResource(R.string.scheduled_delete_title),
            stringResource(R.string.scheduled_delete_body),
            stringResource(R.string.scheduled_delete),
            onDismiss = { deleting = null },
        ) { viewModel.onDelete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduledPostsScreen(
    state: ScheduledUiState,
    onBack: () -> Unit,
    onMove: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.composer_scheduled_posts)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.composer_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(state.posts, key = { it.id }) { post ->
                ScheduledRow(post, onMove = { onMove(post.id) }, onDelete = { onDelete(post.id) })
                HorizontalDivider()
            }
            item(key = "footer") {
                when {
                    state.failed -> Column(
                        Modifier.fillMaxWidth().padding(AlohaSpacing.l),
                        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.scheduled_failed), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onRetry) { Text(stringResource(R.string.scheduled_retry)) }
                    }

                    state.loading -> ListProgress()

                    state.posts.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(AlohaSpacing.l),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.scheduled_none), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledRow(post: ScheduledStatus, onMove: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)) {
        Text(
            stringResource(R.string.composer_scheduled_for, fullDate(post.scheduledAt)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        post.params.spoilerText?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall)
        }
        Text(
            post.params.text.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (post.mediaAttachments.isNotEmpty()) {
            val count = post.mediaAttachments.size
            Text(
                pluralStringResource(R.plurals.composer_media_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // which post each button acts on, said aloud: the list has a pair for every post
        val which = post.params.text.orEmpty().take(EXCERPT)
        val move = stringResource(R.string.scheduled_move_one, which)
        val delete = stringResource(R.string.scheduled_delete_one, which)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onMove, modifier = Modifier.semantics { contentDescription = move }) {
                Text(stringResource(R.string.scheduled_move))
            }
            TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = delete }) {
                Text(stringResource(R.string.scheduled_delete))
            }
        }
    }
}

private const val PREVIEW_LINES = 4
private const val EXCERPT = 40
