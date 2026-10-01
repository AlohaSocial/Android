// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.Trouble
import social.aloha.core.data.timeline.ModeSources
import social.aloha.core.data.timeline.ModeTimeline
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.FeedMode
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource

@Immutable
internal data class AudioUiState(
    val posts: List<AudioItem> = emptyList(),
    val source: TimelineSource = TimelineSource.Home,
    val sources: List<TimelineSource> = listOf(TimelineSource.Home),
    val nowPlaying: NowPlaying? = null,
    val loadedOnce: Boolean = false,
    val trouble: Trouble? = null,
)

/**
 * Audio: the posts with sound from the source chosen for the mode. Playing one plays the list from it
 * on, each named on the lock screen by its first line and who posted it, with its picture or theirs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class AudioViewModel @Inject constructor(
    timelines: TimelineRepository,
    private val modes: ModeSources,
    private val playback: AudioPlayback,
    clock: Clock,
) : ViewModel() {
    private val timeline: StateFlow<ModeTimeline?> =
        modes.timeline(FeedMode.Audio).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val pager = TimelinePager(timelines, clock, viewModelScope) { timeline.value?.let { it.reader to it.key } }

    val uiState: StateFlow<AudioUiState> = timeline.filterNotNull()
        .flatMapLatest { (reader, key, sources) ->
            // the posts' words are read off the main thread, and only when the rows change, not with paging
            val posts = pager.observe(reader, key)
                .map { rows -> rows.mapNotNull { (it as? TimelineRow.Post)?.status?.let(::audioItemOf) } }
                .flowOn(Dispatchers.Default)
            combine(posts, pager.states) { posts, paging ->
                AudioUiState(
                    posts = posts,
                    source = key.source,
                    sources = sources,
                    loadedOnce = paging.loadedOnce,
                    trouble = paging.trouble,
                )
            }
        }
        .combine(playback.nowPlaying) { state, now -> state.copy(nowPlaying = now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), AudioUiState())

    init {
        viewModelScope.launch { timeline.filterNotNull().collect { pager.start(it.reader, it.key) } }
    }

    fun onNearEnd() = pager.older()

    fun onSource(source: TimelineSource) {
        viewModelScope.launch { modes.choose(FeedMode.Audio, source) }
    }

    /** Plays [statusId], the list from there on, or pauses and resumes it where it is the one playing. */
    fun onPlay(statusId: String) {
        val state = uiState.value
        if (state.nowPlaying?.item?.statusId == statusId) return playback.toggle()
        playback.play(state.posts, state.posts.indexOfFirst { it.statusId == statusId })
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

/**
 * A post's first sound, named by the first line of the post, else the sound's description, else who
 * posted it, and pictured by the sound's preview, else the poster; none for a post without sound.
 */
internal fun audioItemOf(status: Status): AudioItem? {
    val shown = status.displayed
    val sound = shown.mediaAttachments.firstOrNull { it.type == AttachmentKind.Audio && it.url != null } ?: return null
    val url = checkNotNull(sound.url)
    val author = shown.account.bestDisplayName
    val title = StatusHtmlParser.firstLine(shown.content) ?: sound.description?.takeIf(String::isNotBlank) ?: author
    return AudioItem(shown.id, url, title, author, sound.previewUrl ?: shown.account.avatar)
}
