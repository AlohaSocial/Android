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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.Trouble
import social.aloha.core.data.timeline.ModeSources
import social.aloha.core.data.timeline.ModeTimeline
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.FeedMode
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineSource
import social.aloha.core.model.VideoSources
import social.aloha.core.sync.DeviceConditions
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
    val loop: Boolean = true,
    /** Whether a short waits for a tap as it comes into view: on mobile data, where the reader asked. */
    val waitForTap: Boolean = false,
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
    timelines: TimelineRepository,
    private val modes: ModeSources,
    private val preferences: ModePreferences,
    private val interactions: StatusInteractions,
    private val cache: RichTextCache,
    private val conditions: DeviceConditions,
    clock: Clock,
) : ViewModel() {
    private val timeline: StateFlow<ModeTimeline?> =
        modes.timeline(FeedMode.Shorts).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pager = TimelinePager(timelines, clock, viewModelScope) { timeline.value?.let { it.reader to it.key } }
    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val failed = MutableStateFlow(false)

    val uiState: StateFlow<ShortsUiState> = timeline.filterNotNull()
        .flatMapLatest { (reader, key, sources) ->
            combine(pager.observe(reader, key), colors.filterNotNull(), pager.states) { rows, colors, paging ->
                val mapper = StatusRowMapper(cache, colors)
                ShortsUiState(
                    shorts = rows.mapNotNull { short(reader, mapper, it) },
                    source = key.source,
                    sources = sources,
                    loadedOnce = paging.loadedOnce,
                    trouble = paging.trouble,
                )
            }
        }
        .combine(preferences.videosMuted) { state, muted -> state.copy(muted = muted) }
        .combine(preferences.loopShorts) { state, loop -> state.copy(loop = loop) }
        // ponytail: the network as the choice is read; a move to mobile data counts from the next change
        .combine(preferences.autoplayOnMobileData) { state, autoplay ->
            state.copy(waitForTap = !autoplay && conditions.metered)
        }
        .combine(failed) { state, failed -> state.copy(actionFailed = failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ShortsUiState())

    init {
        viewModelScope.launch { timeline.filterNotNull().collect { pager.start(it.reader, it.key) } }
    }

    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    /** The pager neared the last short it holds: the next page below. */
    fun onNearEnd() = pager.older()

    fun onSource(source: TimelineSource) {
        viewModelScope.launch { modes.choose(FeedMode.Shorts, source) }
    }

    fun onMuted(muted: Boolean) {
        viewModelScope.launch { preferences.setVideosMuted(muted) }
    }

    fun onToggle(statusId: String, toggle: Toggle) {
        viewModelScope.launch {
            val reader = timeline.value?.reader ?: return@launch
            val status = pager.stored.filterIsInstance<TimelineRow.Post>().map { it.status }
                .firstOrNull { it.displayed.id == statusId }?.displayed ?: return@launch
            if (interactions.toggle(reader, status, toggle) != null) failed.value = true
        }
    }

    fun onActionFailureShown() = failed.update { false }

    private fun short(reader: SignedInAccount, mapper: StatusRowMapper, row: TimelineRow): ShortUi? {
        val post = (row as? TimelineRow.Post)?.status ?: return null
        val shown = post.displayed
        val clip =
            shown.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video || it.type == AttachmentKind.Gifv }
                ?: return null
        val sources = VideoSources.ladder(post, clip, reader.capabilities.apiBase)
        return ShortUi(mapper.map(post, reader.serverAccountId), ShortSource(shown.id, sources), clip)
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
