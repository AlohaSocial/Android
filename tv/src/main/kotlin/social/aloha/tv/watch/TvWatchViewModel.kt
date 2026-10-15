// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.watch

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.data.profile.Reports
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.data.thread.Conversation
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.media.LadderPlayback
import social.aloha.core.media.mediaPlayer
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.VideoChapter
import social.aloha.core.model.VideoChapters
import social.aloha.core.model.VideoSources
import social.aloha.core.model.WatchPositionRules
import social.aloha.core.network.endpoints.ReportDraft

/** A comment under a video: who wrote it and what, as plain text. */
@Immutable
internal data class CommentUi(val id: String, val author: String, val text: String)

/** What a report is about, of the kinds moderators sort them by that need nothing more from the reader. */
internal enum class ReportReason(val wire: String) { Spam("spam"), Legal("legal"), Other("other") }

/** What the watch page's controls do. */
internal class WatchActions(
    val onReveal: () -> Unit,
    val onSeek: (Double) -> Unit,
    val onFollow: () -> Unit,
    val onReport: (ReportReason) -> Unit,
    val onRetry: () -> Unit,
)

@Immutable
internal data class WatchUiState(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val chapters: List<VideoChapter> = emptyList(),
    val comments: List<CommentUi> = emptyList(),
    /** Whether the reader follows who posted it; null for their own video, or before it is known. */
    val following: Boolean? = null,
    /** Marked sensitive, and the reader's policy covers it: it waits behind its warning until asked to play. */
    val covered: Boolean = false,
    val warning: String = "",
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** A follow or a report the server did not take; said until the reader tries again. */
    val actionFailed: Boolean = false,
    val reported: Boolean = false,
)

/**
 * A video's watch page on the TV: the video from the best source its server has, falling back a rung on a
 * 404, from where the reader left off; its chapters, from the server or the description; following who
 * posted it; reporting it; and the replies as comments to read. Where the reader got to is told to the
 * server every ten seconds while it plays, at once when it pauses, and when the page goes. A video marked
 * sensitive waits behind its warning unless the reader's policy shows such media.
 */
@HiltViewModel(assistedFactory = TvWatchViewModel.Factory::class)
internal class TvWatchViewModel @AssistedInject constructor(
    @Assisted private val statusId: String,
    @ApplicationContext context: Context,
    private val accounts: AccountRepository,
    private val threads: ThreadRepository,
    private val watching: WatchPositions,
    private val profiles: ProfileRepository,
    private val reports: Reports,
    private val sensitive: SensitiveMedia,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(statusId: String): TvWatchViewModel
    }

    private val state = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = state.asStateFlow()

    /** The page's one player; it goes with the page. */
    val player: ExoPlayer = mediaPlayer(context)

    private var reader: SignedInAccount? = null
    private var video: Status? = null
    private var ladder: LadderPlayback? = null
    private var playing: Job? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) report(forced = true)
                }
            },
        )
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            // the accounts as read, not the state flow, which is still empty for a moment after a start or a sign-in
            val reader = accounts.activeOnceRead.first()
            val answer = reader?.let { threads.load(it, statusId) }
            if (reader != null && answer is Answer.Got) {
                opened(reader, answer.value)
            } else {
                state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** The video loaded: what the page shows of it, and it plays, unless it waits behind its warning. */
    private suspend fun opened(reader: SignedInAccount, conversation: Conversation) {
        val video = conversation.focused.displayed
        this.reader = reader
        this.video = video
        val covered = video.sensitive && !sensitive.policy(reader.id).first().allowsAutomaticReveal
        val description = StatusHtmlParser.plainText(video.content)
        val title = video.video?.title ?: StatusHtmlParser.firstLine(video.content).orEmpty()
        state.update {
            it.copy(
                title = title,
                author = video.account.bestDisplayName,
                // a post without a video title has its first line as the title, and nothing more to say
                description = description.takeUnless { it.trim() == title.trim() }.orEmpty(),
                chapters = video.video?.chapters.orEmpty().ifEmpty { VideoChapters.parse(description) },
                comments = conversation.descendants.map { comment ->
                    CommentUi(comment.id, comment.account.bestDisplayName, StatusHtmlParser.plainText(comment.content))
                },
                covered = covered,
                warning = video.spoilerText,
                loading = false,
            )
        }
        if (!covered) play(reader, video)
        if (video.account.id != reader.serverAccountId) {
            profiles.relationship(reader, video.account.id)?.let { r ->
                state.update { it.copy(following = r.following) }
            }
        }
    }

    /** The reader asked to see a covered video. */
    fun onReveal() {
        val reader = reader ?: return
        val video = video ?: return
        state.update { it.copy(covered = false) }
        play(reader, video)
    }

    fun onSeek(seconds: Double) {
        player.seekTo((seconds * MILLIS).toLong())
        player.play()
    }

    /** Follows who posted the video, or stops: subscribing to a channel is following its account. */
    fun onFollow() {
        val reader = reader ?: return
        val video = video ?: return
        val following = state.value.following ?: return
        state.update { it.copy(actionFailed = false) }
        viewModelScope.launch {
            val change = if (following) RelationshipChange.Unfollow else RelationshipChange.Follow()
            when (val answer = profiles.change(reader, video.account.id, change)) {
                is Answer.Got -> state.update { it.copy(following = answer.value.following) }
                is Answer.Missed -> state.update { it.copy(actionFailed = true) }
            }
        }
    }

    /** Reports the video to the reader's server's moderators. */
    fun onReport(reason: ReportReason) {
        val reader = reader ?: return
        val video = video ?: return
        state.update { it.copy(actionFailed = false) }
        viewModelScope.launch {
            val draft =
                ReportDraft(video.account.id, comment = "", category = reason.wire, statusIds = listOf(video.id))
            val sent = reports.send(reader, draft) is Answer.Got
            state.update { it.copy(reported = sent, actionFailed = !sent) }
        }
    }

    private fun play(reader: SignedInAccount, video: Status) {
        val attachment = video.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video }
            ?: video.mediaAttachments.firstOrNull()
            ?: return
        val apiBase = reader.capabilities.apiBase
        // a page loaded again, or revealed, plays once: one ladder on the player, one report loop
        playing?.cancel()
        ladder?.release()
        playing = viewModelScope.launch {
            val resumeAt = watching.resumeAt(reader.id, video.id) ?: 0.0
            ladder = LadderPlayback(
                player,
                VideoSources.ladder(video, attachment, apiBase),
                VideoSources.captions(video, apiBase),
            ).also { it.start((resumeAt * MILLIS).toLong()) }
            player.playWhenReady = true
            while (isActive) {
                delay(WatchPositionRules.REPORT_INTERVAL_SECONDS.seconds)
                if (player.isPlaying) report(forced = false)
            }
        }
    }

    private fun report(forced: Boolean) {
        val reader = reader ?: return
        val video = video ?: return
        val length = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val position = player.currentPosition / MILLIS
        // the application's scope: the last report, as the page goes, must outlive the page
        scope.launch { watching.report(reader, video.id, position, length / MILLIS, forced) }
    }

    override fun onCleared() {
        report(forced = true)
        ladder?.release()
        player.release()
    }

    private companion object {
        const val MILLIS = 1_000.0
    }
}
