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
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.ui.fullDate

/** The drafts of one account, newest first; nothing is loaded, they are on the phone. */
@HiltViewModel(assistedFactory = DraftsViewModel.Factory::class)
internal class DraftsViewModel @AssistedInject constructor(@Assisted key: DraftsKey, private val outbox: Outbox) :
    ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: DraftsKey): DraftsViewModel
    }

    /** Null until the outbox has answered, so an empty list is never shown before it is known. */
    val entries: StateFlow<List<OutboxEntry>?> = outbox.observe(key.readerId).map<_, List<OutboxEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), null)

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
    DraftsScreen(entries, onBack, onOpen, onDelete = { deleting = it }, modifier)
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.drafts_delete_title)) },
            text = { Text(stringResource(R.string.drafts_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.onDelete(id)
                }) { Text(stringResource(R.string.drafts_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.composer_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftsScreen(
    entries: List<OutboxEntry>?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.drafts_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.scheduled_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(entries.orEmpty(), key = { it.id }) { entry ->
                DraftRow(entry, onOpen = { onOpen(entry.id) }, onDelete = { onDelete(entry.id) })
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
private fun DraftRow(entry: OutboxEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    val post = entry.post
    val first = post.segments.first()
    val media = post.segments.sumOf { it.media.size }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)
            .padding(start = AlohaSpacing.m, top = AlohaSpacing.s, bottom = AlohaSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    if (post.replyToId != null) R.string.drafts_reply_saved else R.string.drafts_saved,
                    fullDate(entry.updatedAt),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
        IconButton(onClick = onDelete) { Icon(AlohaIcons.Delete, stringResource(R.string.drafts_delete)) }
    }
}

private const val PREVIEW_LINES = 3
private const val EXCERPT = 40
