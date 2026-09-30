// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.notifications.NotificationFiltering
import social.aloha.core.data.previewText
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.NotificationRequest
import social.aloha.core.navigation.NotificationRequestsKey
import social.aloha.core.ui.AccountRow
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.readingColumn

/** [requests] is null while they load; [busy] while a decision is on its way. */
internal data class RequestsUiState(
    val requests: List<NotificationRequest>? = null,
    val failed: Boolean = false,
    val decisionFailed: Boolean = false,
    val busy: Boolean = false,
)

@HiltViewModel(assistedFactory = RequestsViewModel.Factory::class)
internal class RequestsViewModel @AssistedInject constructor(
    @Assisted private val key: NotificationRequestsKey,
    private val accounts: AccountRepository,
    private val filtering: NotificationFiltering,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: NotificationRequestsKey): RequestsViewModel
    }

    private val state = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    /** Lets [ids]'s notifications through; the rows go as soon as the server agrees. */
    fun onAccept(ids: List<String>) = decide(ids, accept = true)

    /** Drops [ids]'s held notifications; the rows go as soon as the server agrees. */
    fun onDismiss(ids: List<String>) = decide(ids, accept = false)

    private fun decide(ids: List<String>, accept: Boolean) {
        if (ids.isEmpty() || state.value.busy) return
        state.update { it.copy(busy = true, decisionFailed = false) }
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId)
            val error = reader?.let { if (accept) filtering.accept(it, ids) else filtering.dismiss(it, ids) }
            // a partial failure leaves the rows that failed; reloading shows which those were
            if (error == null) {
                state.update { current -> current.copy(requests = current.requests?.filterNot { it.id in ids }) }
            } else {
                load()
            }
            state.update { it.copy(busy = false, decisionFailed = error != null) }
        }
    }

    private suspend fun load() {
        val reader = accounts.byId(key.readerId) ?: return
        val answer = filtering.requests(reader)
        state.update { it.copy(requests = (answer as? Answer.Got)?.value, failed = answer is Answer.Missed) }
    }
}

@Composable
public fun RequestsRoute(
    key: NotificationRequestsKey,
    onOpenProfile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<RequestsViewModel, RequestsViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RequestsScreen(state, viewModel::onAccept, viewModel::onDismiss, onOpenProfile, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequestsScreen(
    state: RequestsUiState,
    onAccept: (List<String>) -> Unit,
    onDismiss: (List<String>) -> Unit,
    onOpenProfile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.requests_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.notifications_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.decisionFailed) TroubleStrip(stringResource(R.string.requests_failed))
            val requests = state.requests
            when {
                state.failed -> Message(stringResource(R.string.requests_error))
                requests == null -> ListProgress()
                requests.isEmpty() -> Message(stringResource(R.string.requests_empty))
                else -> Requests(requests, state.busy, onAccept, onDismiss, onOpenProfile)
            }
        }
    }
}

@Composable
private fun Requests(
    requests: List<NotificationRequest>,
    busy: Boolean,
    onAccept: (List<String>) -> Unit,
    onDismiss: (List<String>) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val colors = RichTextColors.fromTheme()
    val mapper = remember(colors) { StatusRowMapper(RichTextCache(), colors) }
    val all = requests.map { it.id }
    LazyColumn(Modifier.readingColumn()) {
        item {
            // a screenful of unknown senders is cleared in one go
            Row(
                Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            ) {
                OutlinedButton(onClick = { onDismiss(all) }, enabled = !busy) {
                    Text(stringResource(R.string.requests_dismiss_all))
                }
                OutlinedButton(onClick = { onAccept(all) }, enabled = !busy) {
                    Text(stringResource(R.string.requests_accept_all))
                }
            }
        }
        items(requests, key = { it.id }) { request ->
            Column(Modifier.padding(bottom = AlohaSpacing.s)) {
                AccountRow(mapper.author(request.account), request.account.emojis, onOpen = {
                    onOpenProfile(request.account.id)
                })
                Text(
                    pluralStringResource(
                        R.plurals.requests_count,
                        request.notificationsCount,
                        request.notificationsCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = AlohaSpacing.m),
                )
                // what the sender wrote last, never what a content warning keeps back
                val preview = remember(request.lastStatus) { request.lastStatus?.displayed?.previewText() }
                preview?.let { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = PREVIEW_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = AlohaSpacing.m),
                    )
                }
                // read aloud with whose they are, since every row has the same two buttons
                val name = request.account.bestDisplayName
                val dismiss = stringResource(R.string.requests_dismiss_from, name)
                val accept = stringResource(R.string.requests_accept_from, name)
                Row(Modifier.padding(horizontal = AlohaSpacing.s)) {
                    TextButton(
                        onClick = { onDismiss(listOf(request.id)) },
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = dismiss },
                    ) { Text(stringResource(R.string.requests_dismiss)) }
                    TextButton(
                        onClick = { onAccept(listOf(request.id)) },
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = accept },
                    ) { Text(stringResource(R.string.requests_accept)) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
