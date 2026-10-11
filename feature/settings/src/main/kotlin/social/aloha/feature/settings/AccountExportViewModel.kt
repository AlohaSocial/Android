// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.model.MigrationListKind
import social.aloha.core.sync.ExportJob
import social.aloha.core.sync.ExportRun
import social.aloha.core.sync.Exports

/** What can be taken away: the whole account as an archive when [list] is null, else one of its lists. */
@Immutable
internal data class ExportItem(val list: MigrationListKind? = null) {
    val isArchive: Boolean get() = list == null

    val job: ExportJob get() = list?.let(ExportJob::ListOf) ?: ExportJob.Archive

    /** The name the file is offered under. */
    fun fileName(handle: String, today: LocalDate): String =
        list?.let { "${it.wire}.csv" } ?: "social-$handle-$today.zip"
}

private fun ExportJob.item(): ExportItem? = when (this) {
    ExportJob.Archive -> ExportItem()
    is ExportJob.ListOf -> ExportItem(kind)
    is ExportJob.Statistics -> null
}

@Immutable
internal data class AccountExportUiState(
    val status: PageStatus = PageStatus.Loading,
    val handle: String = "",
    val working: ExportItem? = null,
    val saved: ExportItem? = null,
    val failed: Boolean = false,
)

/** What the export page asks for. */
internal interface AccountExportActions {
    /** Writes [item] into the document at [uri], which the reader chose. */
    fun onExport(item: ExportItem, uri: String)

    fun onRetry()

    fun onResultShown()
}

/**
 * [readerId]'s account to keep or take elsewhere, each part saved where the reader chooses. The saving
 * runs in [Exports], so it goes on when the reader leaves the page.
 */
@HiltViewModel(assistedFactory = AccountExportViewModel.Factory::class)
internal class AccountExportViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val exports: Exports,
) : ViewModel(),
    AccountExportActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): AccountExportViewModel
    }

    private val state = MutableStateFlow(AccountExportUiState())
    val uiState: StateFlow<AccountExportUiState> = state.asStateFlow()

    /** The export asked for that WorkManager does not have yet, shown as working meanwhile. */
    private var pending: ExportItem? = null
    private val follower = ExportFollower { run ->
        val item = run.job.item() ?: return@ExportFollower
        state.update {
            if (run.outcome == ExportRun.Outcome.Saved) it.copy(saved = item) else it.copy(failed = true)
        }
    }

    init {
        load()
        viewModelScope.launch {
            exports.observe(readerId).collect { runs ->
                val running = runs.firstNotNullOfOrNull { run ->
                    run.job.item().takeIf { run.outcome == ExportRun.Outcome.Running }
                }
                state.update { it.copy(working = running ?: pending) }
                follower.seen(runs)
            }
        }
    }

    override fun onRetry() = load()

    override fun onResultShown() = state.update { it.copy(saved = null, failed = false) }

    override fun onExport(item: ExportItem, uri: String) {
        if (state.value.working != null) return
        pending = item
        state.update { it.copy(working = item) }
        viewModelScope.launch {
            // asked for is asked for: leaving the page right away must not lose it
            val id = withContext(NonCancellable) { exports.enqueue(readerId, item.job, uri.toUri()) }
            pending = null
            follower.asked(id)
        }
    }

    private fun load() {
        viewModelScope.launch {
            val account = accounts.byId(readerId)
            state.update {
                it.copy(status = pageStatusOf(account) ?: PageStatus.Ready, handle = account?.handle.orEmpty())
            }
        }
    }
}
