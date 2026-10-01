// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.media.LadderPlayback
import social.aloha.core.media.mediaPlayer
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.ContentClassifier
import social.aloha.core.model.ContentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.VideoSources
import social.aloha.core.model.WatchPositionRules

/**
 * The one video playing, wherever it is shown: on its watch page, in the picture-in-picture window, or
 * only heard, with the app in the background and its controls on the lock screen. It outlives a screen
 * so leaving the page for picture-in-picture, or turning the phone, does not start the video over. It
 * tells the server where the reader got to every ten seconds while playing, at once on a pause, and when
 * it stops; and what it sees of a clip the server did not describe settles whether it is a short. A
 * switch to another account stops it, as does signing its account out, which then reports nothing.
 */
@Singleton
public class VideoPlayback @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val watching: WatchPositions,
    private val statuses: StatusRepository,
    private val accounts: AccountRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private class Playing(val reader: SignedInAccount, val video: Status, val ladder: LadderPlayback)

    private var playing: Playing? = null
    private var ticker: Job? = null
    private val pictureInPicture = MutableStateFlow(false)

    /** Whether leaving the app should shrink the video into a window of its own: while its page shows it playing. */
    public val wantsPictureInPicture: StateFlow<Boolean> = pictureInPicture.asStateFlow()

    /**
     * Whether the video fills a picture-in-picture window, which the activity says. The watch page lets
     * go of the player meanwhile, so the window's view has it, and takes it back after.
     */
    public val inPictureInPicture: MutableStateFlow<Boolean> = MutableStateFlow(false)

    public val player: ExoPlayer by lazy {
        mediaPlayer(context).also { it.addListener(listener) }
    }

    init {
        scope.launch(Dispatchers.Main) {
            accounts.activeAccount.map { it?.id }.distinctUntilChanged().collect { active ->
                val now = playing?.reader ?: return@collect
                // a signed-out account is gone from the server's view and from the cache: nothing to report
                if (now.id != active) stop(report = accounts.byId(now.id) != null)
            }
        }
    }

    /** Plays [video] from the best source its server has; the video already playing just goes on. */
    internal fun play(reader: SignedInAccount, video: Status, startAtSeconds: Double?): Unit = onPlayerThread {
        if (playing?.let { it.video.id == video.id && it.reader.id == reader.id } == true) return@onPlayerThread
        stop()
        val ladder = ladderFor(reader, video)
        playing = Playing(reader, video, ladder)
        ladder.start(((startAtSeconds ?: 0.0) * MILLIS).toLong())
        player.playWhenReady = true
        // the session, its notification and lock screen controls, for as long as there is something to play
        context.startService(Intent(context, VideoPlaybackService::class.java))
        ticker = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(WatchPositionRules.REPORT_INTERVAL_SECONDS.seconds)
                if (player.isPlaying) report(forced = false)
            }
        }
    }

    /** [video] from the best source its server has, named on the lock screen and in the notification. */
    private fun ladderFor(reader: SignedInAccount, video: Status): LadderPlayback {
        val apiBase = reader.capabilities.apiBase
        val attachment = video.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video }
            ?: video.mediaAttachments.firstOrNull()
        val sources = attachment?.let { VideoSources.ladder(video, it, apiBase) }.orEmpty()
        return LadderPlayback(player, sources, VideoSources.captions(video, apiBase), metadataOf(video, attachment))
    }

    /** What the lock screen and the notification call [video]: its title, else its first line, and who posted it. */
    private fun metadataOf(video: Status, attachment: MediaAttachment?): MediaMetadata = MediaMetadata.Builder()
        .setTitle(video.video?.title ?: StatusHtmlParser.firstLine(video.content))
        .setArtist(video.account.bestDisplayName)
        .setArtworkUri(attachment?.previewUrl?.toUri())
        .build()

    /** Stops [statusId], or whatever plays when none is named, telling the server where it got to when [report]. */
    internal fun stop(statusId: String? = null, report: Boolean = true): Unit = onPlayerThread {
        val now = playing ?: return@onPlayerThread
        if (statusId != null && now.video.id != statusId) return@onPlayerThread
        if (report) report(forced = true)
        ticker?.cancel()
        // gone before the player stops, so the pause that stopping is reports nothing a second time
        playing = null
        now.ladder.release()
        player.stop()
        player.clearMediaItems()
        pictureInPicture.value = false
    }

    /** The watch page shows the video, or no longer does. */
    internal fun onShown(shown: Boolean) {
        pictureInPicture.value = shown && playing != null
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) report(forced = true)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val now = playing ?: return
            if (videoSize.width == 0 || player.duration == C.TIME_UNSET) return
            if (ContentClassifier.classify(now.video) != ContentKind.Undetermined) return
            val attachment = now.video.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video } ?: return
            val seen = MediaDimensions(videoSize.width, videoSize.height, duration = player.duration / MILLIS)
            scope.launch { statuses.reclassify(now.reader.id, now.video.id, attachment.id, seen) }
        }
    }

    /** The player is only ever touched on its own thread, whichever thread asks. */
    private fun onPlayerThread(block: () -> Unit) {
        val looper = player.applicationLooper
        if (Looper.myLooper() == looper) block() else Handler(looper).post(block)
    }

    private fun report(forced: Boolean) {
        val now = playing ?: return
        val length = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val position = player.currentPosition / MILLIS
        scope.launch { watching.report(now.reader, now.video.id, position, length / MILLIS, forced) }
    }

    private companion object {
        const val MILLIS = 1_000.0
    }
}
