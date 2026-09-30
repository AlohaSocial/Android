// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.OutboxEntry
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.OutboxState
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.sync.PostQueue
import social.aloha.core.ui.ConfirmDialog
import social.aloha.core.ui.fullDate

/**
 * The drafts of one account and its posts waiting to go out, newest first; nothing is loaded, they
 * are on the phone. A queued post opened is taken out of the queue until it is posted again.
 */
@HiltViewModel(assistedFactory = DraftsViewModel.Factory::class)
internal class DraftsViewModel @AssistedInject constructor(
    @Assisted private val key: DraftsKey,
    private val outbox: Outbox,
    private val queue: PostQueue,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: DraftsKey): DraftsViewModel
    }

    /** Null until the outbox has answered, so an empty list is never shown before it is known. */
    val entries: StateFlow<List<OutboxEntry>?> = outbox.observe(key.readerId).map<_, List<OutboxEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), null)

    /** Sends the posts that waited for a new sign-in. */
    fun onResume() {
        viewModelScope.launch { queue.resume(key.readerId) }
    }

    /** Deletes draft [id] and the files it kept. */
    fun onDelete(id: String) {
        viewModelScope.launch { outbox.delete(id, files = true) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

@Composable
public fun DraftsRoute(key: DraftsKey, onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<DraftsViewModel, DraftsViewModel.Factory>(key = key.toString()) { it.create(key) }
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    DraftsScreen(entries, DraftsActions(onBack, onOpen, onDelete = { deleting = it }, viewModel::onResume), modifier)
    deleting?.let { id ->
        ConfirmDialog(
            stringResource(R.string.drafts_delete_title),
            stringResource(R.string.drafts_delete_body),
            stringResource(R.string.drafts_delete),
            onDismiss = { deleting = null },
        ) { viewModel.onDelete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftsScreen(entries: List<OutboxEntry>?, actions: DraftsActions, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.drafts_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.composer_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(entries.orEmpty(), key = { it.id }) { entry ->
                DraftRow(entry, actions)
                HorizontalDivider()
            }
            if (entries?.isEmpty() == true) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.drafts_none), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftRow(entry: OutboxEntry, actions: DraftsActions) {
    val post = entry.post
    val first = post.segments.first()
    val media = post.segments.sumOf { it.media.size }
    // one going out cannot be changed; it is gone in a moment
    val sending = entry.state == OutboxState.Sending
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !sending, role = Role.Button) { actions.onOpen(entry.id) }
            .padding(start = AlohaSpacing.m, top = AlohaSpacing.s, bottom = AlohaSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            StateLine(entry)
            post.spoiler?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(
                first.text.ifBlank { stringResource(R.string.drafts_no_text) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            val extras = listOfNotNull(
                pluralStringResource(R.plurals.drafts_segments, post.segments.size, post.segments.size)
                    .takeIf { post.segments.size > 1 },
                pluralStringResource(R.plurals.composer_media_count, media, media).takeIf { media > 0 },
                stringResource(R.string.drafts_poll).takeIf { post.poll != null },
                post.scheduledAt?.let { stringResource(R.string.composer_scheduled_for, fullDate(it)) },
            )
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entry.state == OutboxState.Paused) {
            TextButton(onClick = actions.onResume) { Text(stringResource(R.string.drafts_resume)) }
        }
        IconButton(onClick = { actions.onDelete(entry.id) }, enabled = !sending) {
            // which draft goes, said aloud: a list of them all has a delete button each
            Icon(AlohaIcons.Delete, stringResource(R.string.drafts_delete_one, first.text.take(EXCERPT)))
        }
    }
}

/** What the drafts list's controls do. */
internal class DraftsActions(
    val onBack: () -> Unit,
    val onOpen: (String) -> Unit,
    val onDelete: (String) -> Unit,
    /** Sends the posts that waited for a new sign-in. */
    val onResume: () -> Unit,
)

/** Where the post stands: kept as a draft, waiting, going out, or needing the writer. */
@Composable
private fun StateLine(entry: OutboxEntry) {
    val reply = entry.post.replyToId != null
    val (text, error) = when (entry.state) {
        OutboxState.Draft -> stringResource(
            if (reply) R.string.drafts_reply_saved else R.string.drafts_saved,
            fullDate(entry.updatedAt),
        ) to false

        OutboxState.Queued -> stringResource(R.string.drafts_queued) to false

        OutboxState.Sending -> stringResource(R.string.drafts_sending) to false

        OutboxState.Paused -> stringResource(R.string.drafts_paused) to true

        OutboxState.Failed -> (
            entry.error?.let { stringResource(R.string.drafts_failed, it) }
                ?: stringResource(R.string.drafts_failed_unknown)
            ) to true
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

private const val PREVIEW_LINES = 3
private const val EXCERPT = 40
