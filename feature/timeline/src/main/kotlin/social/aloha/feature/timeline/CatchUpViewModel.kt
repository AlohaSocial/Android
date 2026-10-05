// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.TimelinePosition
import social.aloha.core.data.timeline.TimelinePositions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.navigation.CatchUpKey
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowCache
import social.aloha.core.ui.StatusRowUi

@Immutable
internal data class CatchUpUiState(
    val rows: List<StatusRowUi> = emptyList(),
    val people: List<CatchUpPerson> = emptyList(),
    val choice: CatchUpChoice = CatchUpChoice(),
    /** How many arrived, whatever is chosen of them. */
    val arrived: Int = 0,
    val loading: Boolean = true,
    val now: Instant = Instant.EPOCH,
    /** The marker moved to the newest: the page is done with. */
    val done: Boolean = false,
    /** The newest post arrived, where the marker moves once caught up. */
    val newest: String? = null,
)

internal interface CatchUpActions {
    fun onSort(sort: CatchUpSort)

    fun onKind(kind: CatchUpKind)

    fun onPerson(id: String?)

    /** Moves the read marker to the newest post arrived, here and on the server. */
    fun onCaughtUp()
}

/**
 * What arrived on Home since the reader last read it, on one page: the stored rows above where they were,
 * and what a refresh on opening brings. Nothing is kept beyond what the timeline's cache holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = CatchUpViewModel.Factory::class)
internal class CatchUpViewModel @AssistedInject constructor(
    @Assisted private val key: CatchUpKey,
    private val accounts: AccountRepository,
    private val timelines: TimelineRepository,
    private val positions: TimelinePositions,
    private val cache: RichTextCache,
    private val clock: Clock,
) : ViewModel(),
    CatchUpActions {
    @AssistedFactory
    interface Factory {
        fun create(key: CatchUpKey): CatchUpViewModel
    }

    private val reader = MutableStateFlow<SignedInAccount?>(null)
    private val since = MutableStateFlow<String?>(null)
    private val choice = MutableStateFlow(CatchUpChoice())
    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val progress = MutableStateFlow(Progress())

    // a row built once for each post, whichever order or filter shows it
    private val rows = StatusRowCache(cache)

    private data class Progress(val loading: Boolean = true, val done: Boolean = false)

    val uiState: StateFlow<CatchUpUiState> = combine(
        reader.filterNotNull().flatMapLatest { timelines.observe(it, TimelineKey.home()) },
        since,
        choice,
        colors.filterNotNull(),
        progress,
    ) { stored, since, choice, colors, progress ->
        val news = newsOf(stored, since)
        this.rows.use(colors)
        val viewer = reader.value?.serverAccountId.orEmpty()
        CatchUpUiState(
            rows = chosen(news, choice).map { this.rows.rowFor(it, viewer, null) },
            people = peopleOf(news),
            choice = choice,
            arrived = news.size,
            loading = progress.loading,
            now = clock.instant(),
            done = progress.done,
            newest = news.firstOrNull()?.id,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), CatchUpUiState())

    init {
        viewModelScope.launch {
            val account = accounts.byId(key.readerId) ?: return@launch
            since.value = positions.lastRead(account)
            reader.value = account
            val home = TimelineKey.home()
            val top = timelines.observe(account, home).first().firstOrNull()?.id
            timelines.refresh(account, home, RefreshPlan.of(timelines.lastFetched(account, home) != null, top))
            progress.update { it.copy(loading = false) }
        }
    }

    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    override fun onSort(sort: CatchUpSort) = choice.update { it.copy(sort = sort) }

    override fun onKind(kind: CatchUpKind) = choice.update { it.copy(kind = kind) }

    override fun onPerson(id: String?) = choice.update { it.copy(person = id) }

    override fun onCaughtUp() {
        val account = reader.value
        val top = uiState.value.newest
        viewModelScope.launch {
            if (account != null && top != null) {
                positions.markHomeRead(account, top)
                positions.save(account, TimelineKey.home(), TimelinePosition(top, 0))
            }
            progress.update { it.copy(done = true) }
        }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
