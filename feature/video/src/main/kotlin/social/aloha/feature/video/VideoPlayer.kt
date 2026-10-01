// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import social.aloha.core.media.LadderPlayback
import social.aloha.core.media.mediaPlayer
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.VideoCaption
import social.aloha.core.model.VideoSource
import social.aloha.core.model.WatchPositionRules

/** A player for one screen, released when the screen goes. */
@Composable
internal fun rememberVideoPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember { mediaPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * The video, with the player's own controls: play and pause, seeking, the playback speed from 0.5× to
 * 2×, and the subtitle and audio tracks the video has. It starts at [startAtSeconds] and plays from the
 * best of [sources], falling to the next where one fails. [onProgress] hears where the reader got to,
 * every ten seconds while playing and at once on a pause and when the screen goes; [onSeen] what the
 * player found the clip to be. Leaving the app pauses it.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(
    player: ExoPlayer,
    sources: List<VideoSource>,
    captions: List<VideoCaption>,
    startAtSeconds: Double?,
    onProgress: (position: Double, duration: Double, forced: Boolean) -> Unit,
    onSeen: (MediaDimensions) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by rememberUpdatedState(onProgress)
    val seen by rememberUpdatedState(onSeen)
    DisposableEffect(player, sources) {
        val ladder = LadderPlayback(player, sources, captions)
        ladder.start(((startAtSeconds ?: 0.0) * MILLIS).toLong())
        player.playWhenReady = true
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) player.report(forced = true, progress)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width == 0 || player.duration == C.TIME_UNSET) return
                seen(MediaDimensions(videoSize.width, videoSize.height, duration = player.duration / MILLIS))
            }
        }
        player.addListener(listener)
        onDispose {
            player.report(forced = true, progress)
            player.removeListener(listener)
            ladder.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(WatchPositionRules.REPORT_INTERVAL_SECONDS.seconds)
            if (player.isPlaying) player.report(forced = false, progress)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                setShowSubtitleButton(true)
                setShowNextButton(false)
                setShowPreviousButton(false)
                setKeepScreenOn(true)
            }
        },
        modifier = modifier.fillMaxWidth().aspectRatio(WIDE).background(Color.Black),
    )
}

private fun Player.report(forced: Boolean, onProgress: (Double, Double, Boolean) -> Unit) {
    val length = duration.takeIf { it != C.TIME_UNSET } ?: return
    onProgress(currentPosition / MILLIS, length / MILLIS, forced)
}

private const val MILLIS = 1_000.0
private const val WIDE = 16f / 9f
