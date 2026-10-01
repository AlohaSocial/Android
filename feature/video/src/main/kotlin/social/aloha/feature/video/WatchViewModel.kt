// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
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
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.html.RichTextCache
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.ContentClassifier
import social.aloha.core.model.ContentKind
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.VideoCaption
import social.aloha.core.model.VideoChapter
import social.aloha.core.model.VideoChapters
import social.aloha.core.model.VideoSource
import social.aloha.core.model.VideoSources
import social.aloha.core.navigation.WatchKey
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

@Immutable
internal data class WatchUiState(
    val video: StatusRowUi? = null,
    val sources: List<VideoSource> = emptyList(),
    val captions: List<VideoCaption> = emptyList(),
    /** Where to start, in seconds: where the reader left off. */
    val resumeAt: Double? = null,
    val chapters: List<VideoChapter> = emptyList(),
    val comments: List<StatusRowUi> = emptyList(),
    /** Whether the reader follows who posted it; null for their own video, or before it is known. */
    val following: Boolean? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** A change the server did not take; said once. */
    val actionFailed: Boolean = false,
)

/**
 * A video's watch page: the video from the best source its server has, from where the reader left off;
 * its chapters, from the server or the description; following who posted it; and the replies as its
 * comments. How far the reader got goes to [WatchPositions] as they watch, and what the player sees of
 * an undescribed clip settles whether it is a short.
 */
@HiltViewModel(assistedFactory = WatchViewModel.Factory::class)
internal class WatchViewModel @AssistedInject constructor(
    @Assisted private val key: WatchKey,
    private val accounts: AccountRepository,
    private val threads: ThreadRepository,
    private val statuses: StatusRepository,
    private val interactions: StatusInteractions,
    private val profiles: ProfileRepository,
    private val watching: WatchPositions,
    private val cache: RichTextCache,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: WatchKey): WatchViewModel
    }

    private val state = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = state.asStateFlow()

    private var colors: RichTextColors? = null
    private var posts: List<Status> = emptyList()

    /** Rows are rendered with the theme's colours, which only the screen knows; loading waits for them. */
    fun onColors(value: RichTextColors) {
        val first = colors == null
        colors = value
        if (first) onRetry() else viewModelScope.launch { redraw() }
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val reader = reader() ?: return@launch state.update { it.copy(loading = false, failed = true) }
            when (val answer = threads.load(reader, key.statusId)) {
                is Answer.Missed -> state.update { it.copy(loading = false, failed = true) }

                is Answer.Got -> {
                    val video = answer.value.focused.displayed
                    posts = listOf(video) + answer.value.descendants
                    val attachment = video.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video }
                        ?: video.mediaAttachments.firstOrNull()
                    val apiBase = reader.capabilities.apiBase
                    val chapters = video.video?.chapters.orEmpty()
                        .ifEmpty { VideoChapters.parse(StatusHtmlParser.plainText(video.content)) }
                    state.update {
                        it.copy(
                            sources = attachment?.let { media -> VideoSources.ladder(video, media, apiBase) }.orEmpty(),
                            captions = VideoSources.captions(video, apiBase),
                            resumeAt = watching.resumeAt(reader.id, video.id),
                            chapters = chapters,
                            loading = false,
                        )
                    }
                    redraw()
                    following(reader, video)
                }
            }
        }
    }

    /** Where the reader got to; [forced] by a pause or by leaving, otherwise as the video plays. */
    fun onProgress(positionSeconds: Double, durationSeconds: Double, forced: Boolean) {
        val video = posts.firstOrNull() ?: return
        viewModelScope.launch {
            reader()?.let { watching.report(it, video.id, positionSeconds, durationSeconds, forced) }
        }
    }

    /** What the player saw of the clip, which settles one the server did not describe. */
    fun onSeen(dimensions: MediaDimensions) {
        val video = posts.firstOrNull() ?: return
        if (ContentClassifier.classify(video) != ContentKind.Undetermined) return
        val attachment = video.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video } ?: return
        viewModelScope.launch {
            reader()?.let { statuses.reclassify(it.id, video.id, attachment.id, dimensions) }
        }
    }

    fun onToggle(statusId: String, toggle: Toggle) {
        viewModelScope.launch {
            val reader = reader() ?: return@launch
            val status = statuses.get(reader.id, statusId) ?: return@launch
            if (interactions.toggle(reader, status, toggle) != null) state.update { it.copy(actionFailed = true) }
            posts = posts.map { statuses.get(reader.id, it.id) ?: it }
            redraw()
        }
    }

    /** Follows who posted the video, or stops: subscribing to a channel is following its account. */
    fun onFollow() {
        val video = posts.firstOrNull() ?: return
        val following = state.value.following ?: return
        viewModelScope.launch {
            val reader = reader() ?: return@launch
            val change = if (following) RelationshipChange.Unfollow else RelationshipChange.Follow()
            when (val answer = profiles.change(reader, video.account.id, change)) {
                is Answer.Got -> state.update { it.copy(following = answer.value.following) }
                is Answer.Missed -> state.update { it.copy(actionFailed = true) }
            }
        }
    }

    fun onActionFailureShown() = state.update { it.copy(actionFailed = false) }

    private suspend fun following(reader: SignedInAccount, video: Status) {
        if (video.account.id == reader.serverAccountId) return
        val relationship = profiles.relationship(reader, video.account.id) ?: return
        state.update { it.copy(following = relationship.following) }
    }

    private suspend fun redraw() {
        val reader = reader() ?: return
        val mapper = StatusRowMapper(cache, colors ?: return)
        val rows = posts.map { mapper.map(it, reader.serverAccountId) }
        state.update { it.copy(video = rows.firstOrNull(), comments = rows.drop(1)) }
    }

    private suspend fun reader() = accounts.byId(key.readerId)
}
