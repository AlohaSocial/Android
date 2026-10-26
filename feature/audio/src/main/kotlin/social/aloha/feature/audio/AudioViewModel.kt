// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.Trouble
import social.aloha.core.data.timeline.ModeFeed
import social.aloha.core.data.timeline.ModeFeeds
import social.aloha.core.data.timeline.ModeSources
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.FeedMode
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.SeenBy
import social.aloha.core.ui.heldId

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
 * Audio: the posts with sound from the source chosen for the mode; with [together], every account's
 * merged, a post another account holds under its server's name. Playing one plays the list from it on,
 * each named on the lock screen by its first line and who posted it, with its picture or theirs.
 */
@HiltViewModel(assistedFactory = AudioViewModel.Factory::class)
internal class AudioViewModel @AssistedInject constructor(
    @Assisted together: Boolean,
    feeds: ModeFeeds,
    private val modes: ModeSources,
    private val playback: AudioPlayback,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(together: Boolean): AudioViewModel
    }

    private val feed = feeds.of(viewModelScope, FeedMode.Audio, together)

    // the posts' words are read off the main thread, and only when the posts change, not with paging
    private val posts = feed.pages.map { it.posts }.distinctUntilChanged()
        .map { posts -> posts.mapNotNull(::audioItemOf) }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<AudioUiState> = combine(posts, feed.pages, playback.nowPlaying) { posts, page, now ->
        AudioUiState(
            posts = posts,
            source = page.source,
            sources = page.sources,
            nowPlaying = now,
            loadedOnce = page.loadedOnce,
            trouble = page.trouble,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), AudioUiState())

    fun onNearEnd() = feed.older()

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
    return AudioItem(shown.id, url, title, author, sound.previewUrl ?: shown.account.avatar, shown.url)
}

/** [post]'s sound, held by another account than the one in use under its server's name, and who sees it. */
internal fun audioItemOf(post: ModeFeed.Post): AudioItem? = audioItemOf(post.status)?.let { item ->
    item.copy(
        statusId = if (post.inUse) item.statusId else heldId(post.account.host, item.statusId),
        seenBy = post.seenBy.map { SeenBy(it.reader.qualifiedHandle, it.reader.avatarUrl, it.colour) },
    )
}
