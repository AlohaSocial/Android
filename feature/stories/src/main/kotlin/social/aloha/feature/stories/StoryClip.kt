// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import social.aloha.core.media.Sound
import social.aloha.core.media.mediaPlayer
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.Story

/** A story's clip, played once without controls; [onProgress] hears how far, [onDone] its end. */
@OptIn(UnstableApi::class)
@Composable
internal fun StoryClip(story: Story, paused: Boolean, onProgress: suspend (Float) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val player = remember { mediaPlayer(context, if (story.type == AttachmentKind.Gifv) Sound.Silent else Sound.Film) }
    val done by rememberUpdatedState(onDone)
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(story.id) {
        // a clip without an address plays nothing, rather than the story before it again
        story.url?.let { player.setMediaItem(MediaItem.fromUri(it)) } ?: player.clearMediaItems()
        player.prepare()
        val ended = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) done()
            }
        }
        player.addListener(ended)
        onDispose { player.removeListener(ended) }
    }
    LaunchedEffect(story.id, paused) {
        player.playWhenReady = !paused
        while (!paused) {
            val length = player.duration
            if (length > 0) onProgress((player.currentPosition.toFloat() / length).coerceIn(0f, 1f))
            delay(TICK_MILLIS)
        }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private const val TICK_MILLIS = 100L
