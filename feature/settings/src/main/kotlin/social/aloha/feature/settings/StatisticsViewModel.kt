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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.AccountStatistics
import social.aloha.core.sync.ExportJob
import social.aloha.core.sync.ExportRun
import social.aloha.core.sync.Exports

@Immutable
internal data class StatisticsUiState(
    val status: PageStatus = PageStatus.Loading,
    /** How many days back the numbers go; 0 for everything. */
    val days: Int = DEFAULT_DAYS,
    val statistics: AccountStatistics? = null,
    val handle: String = "",
    /** Counting again or for another window, with the last numbers still shown. */
    val refreshing: Boolean = false,
    val exporting: Boolean = false,
    val exported: Boolean = false,
    val failed: Boolean = false,
) {
    companion object {
        const val DEFAULT_DAYS = 90

        /** The windows the server counts over, as it lists them: a month, a season, a year, everything. */
        val WINDOWS = listOf(30, 90, 365, 0)
    }
}

/** What the statistics page asks for. */
internal interface StatisticsActions {
    fun onWindow(days: Int)

    /** Counts again rather than reading what the server kept from the last count. */
    fun onCountAgain()

    /** Writes the numbers as a CSV into the document at [uri], which the reader chose. */
    fun onExport(uri: String)

    fun onRetry()

    fun onResultShown()
}

/** [readerId]'s statistics, counted by the server over a window the reader picks. */
@HiltViewModel(assistedFactory = StatisticsViewModel.Factory::class)
internal class StatisticsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val extras: NextcloudExtras,
    private val exports: Exports,
) : ViewModel(),
    StatisticsActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): StatisticsViewModel
    }

    private val state = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = state.asStateFlow()
    private var counting: Job? = null

    /** Asked for and not with WorkManager yet, shown as exporting meanwhile. */
    private var pending = false
    private val follower = ExportFollower { run ->
        if (run.job !is ExportJob.Statistics) return@ExportFollower
        val saved = run.outcome == ExportRun.Outcome.Saved
        state.update { it.copy(exported = saved, failed = !saved) }
    }

    init {
        load(fresh = false)
        viewModelScope.launch {
            exports.observe(readerId).collect { runs ->
                val running = runs.any { it.job is ExportJob.Statistics && it.outcome == ExportRun.Outcome.Running }
                state.update { it.copy(exporting = running || pending) }
                follower.seen(runs)
            }
        }
    }

    override fun onWindow(days: Int) {
        if (days == state.value.days) return
        state.update { it.copy(days = days) }
        load(fresh = false)
    }

    /** The window the numbers on screen were counted over, which an export keeps to. */
    private val shownDays: Int get() = state.value.statistics?.window?.days ?: state.value.days

    override fun onCountAgain() = load(fresh = true)

    override fun onRetry() = load(fresh = false)

    override fun onResultShown() = state.update { it.copy(exported = false, failed = false) }

    override fun onExport(uri: String) {
        if (state.value.exporting) return
        pending = true
        state.update { it.copy(exporting = true) }
        val job = ExportJob.Statistics(shownDays)
        viewModelScope.launch {
            // asked for is asked for: leaving the page right away must not lose it
            val id = withContext(NonCancellable) { exports.enqueue(readerId, job, uri.toUri()) }
            pending = false
            follower.asked(id)
        }
    }

    private fun load(fresh: Boolean) {
        counting?.cancel()
        state.update { if (it.statistics == null) it.copy(status = PageStatus.Loading) else it.copy(refreshing = true) }
        counting = viewModelScope.launch {
            val account = accounts.nextcloudReader(readerId) { blocked ->
                state.update { it.copy(status = blocked) }
            } ?: return@launch
            val answer = extras.statistics(account, state.value.days, fresh)
            state.update {
                when (answer) {
                    is Answer.Got -> it.copy(
                        status = PageStatus.Ready,
                        statistics = answer.value,
                        handle = account.handle,
                        refreshing = false,
                    )

                    // numbers already shown stay, and so does the window they were counted over
                    is Answer.Missed -> if (it.statistics == null) {
                        it.copy(status = PageStatus.Failed, refreshing = false)
                    } else {
                        it.copy(refreshing = false, failed = true, days = it.statistics.window?.days ?: it.days)
                    }
                }
            }
        }
    }
}
