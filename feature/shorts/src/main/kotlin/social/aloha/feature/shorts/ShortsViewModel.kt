// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Trouble
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.FeedMode
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.model.VideoSources
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/** One short: its post as a row (caption, author, counts), where it plays from and its still. */
@Immutable
internal data class ShortUi(val row: StatusRowUi, val source: ShortSource, val clip: MediaAttachment)

@Immutable
internal data class ShortsUiState(
    val shorts: List<ShortUi> = emptyList(),
    val source: TimelineSource = TimelineSource.Home,
    /** Where shorts can come from on this server: the people followed, this server, everyone. */
    val sources: List<TimelineSource> = listOf(TimelineSource.Home),
    val muted: Boolean = true,
    val loadedOnce: Boolean = false,
    val trouble: Trouble? = null,
    val actionFailed: Boolean = false,
)

/**
 * Shorts: the Shorts timeline of the source chosen for the mode, one short per page, newest first and
 * older ones below. Sound stays off until the person turns it on, and that choice holds across sessions
 * and modes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class ShortsViewModel @Inject constructor(
    accounts: AccountRepository,
    timelines: TimelineRepository,
    private val settings: AccountSettingsStore,
    private val preferences: ModePreferences,
    private val interactions: StatusInteractions,
    private val cache: RichTextCache,
    clock: Clock,
) : ViewModel() {
    private val account: StateFlow<SignedInAccount?> =
        accounts.activeAccount.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val key: Flow<TimelineKey> = account.filterNotNull()
        .flatMapLatest { reader -> settings.settings(reader.id).map { reader to it } }
        .map { (reader, chosen) ->
            val source = chosen.modeSources[FeedMode.Shorts.key]?.takeIf { it in sourcesOf(reader) }
                ?: TimelineSource.Home
            TimelineKey(FeedMode.Shorts, source)
        }
        .distinctUntilChanged()

    private val pager = TimelinePager(timelines, clock, viewModelScope) { current() }
    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val failed = MutableStateFlow(false)

    val uiState: StateFlow<ShortsUiState> = combine(account.filterNotNull(), key, ::Pair)
        .distinctUntilChanged { a, b -> a.first.id == b.first.id && a.second == b.second }
        .flatMapLatest { (reader, key) ->
            combine(pager.observe(reader, key), colors.filterNotNull(), pager.states) { rows, colors, paging ->
                val mapper = StatusRowMapper(cache, colors)
                ShortsUiState(
                    shorts = rows.mapNotNull { short(reader, mapper, it) },
                    source = key.source,
                    sources = sourcesOf(reader),
                    loadedOnce = paging.loadedOnce,
                    trouble = paging.trouble,
                )
            }
        }
        .combine(preferences.videosMuted) { state, muted -> state.copy(muted = muted) }
        .combine(failed) { state, failed -> state.copy(actionFailed = failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ShortsUiState())

    init {
        viewModelScope.launch {
            combine(account.filterNotNull(), key, ::Pair).distinctUntilChanged { a, b ->
                a.first.id == b.first.id && a.second == b.second
            }.collect { (reader, key) -> pager.start(reader, key) }
        }
    }

    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    /** The pager neared the last short it holds: the next page below. */
    fun onNearEnd() = pager.older()

    fun onSource(source: TimelineSource) {
        viewModelScope.launch {
            val reader = account.value ?: return@launch
            settings.update(reader.id) { it.copy(modeSources = it.modeSources + (FeedMode.Shorts.key to source)) }
        }
    }

    fun onMuted(muted: Boolean) {
        viewModelScope.launch { preferences.setVideosMuted(muted) }
    }

    fun onToggle(statusId: String, toggle: Toggle) {
        viewModelScope.launch {
            val reader = account.value ?: return@launch
            val status = pager.stored.filterIsInstance<TimelineRow.Post>().map { it.status }
                .firstOrNull { it.displayed.id == statusId }?.displayed ?: return@launch
            if (interactions.toggle(reader, status, toggle) != null) failed.value = true
        }
    }

    fun onActionFailureShown() = failed.update { false }

    private suspend fun current(): Pair<SignedInAccount, TimelineKey>? {
        val reader = account.value ?: return null
        return reader to key.first()
    }

    private fun short(reader: SignedInAccount, mapper: StatusRowMapper, row: TimelineRow): ShortUi? {
        val post = (row as? TimelineRow.Post)?.status ?: return null
        val shown = post.displayed
        val clip =
            shown.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video || it.type == AttachmentKind.Gifv }
                ?: return null
        val sources = VideoSources.ladder(post, clip, reader.capabilities.apiBase)
        return ShortUi(mapper.map(post, reader.serverAccountId), ShortSource(shown.id, sources), clip)
    }

    /** The people followed, and this server and everyone where the server serves those timelines. */
    private fun sourcesOf(reader: SignedInAccount): List<TimelineSource> = buildList {
        add(TimelineSource.Home)
        if (reader.capabilities.localFeed) add(TimelineSource.Local)
        if (reader.capabilities.federatedFeed) add(TimelineSource.Federated)
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
