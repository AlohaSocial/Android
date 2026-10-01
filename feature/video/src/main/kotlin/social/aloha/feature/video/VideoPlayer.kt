// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

/**
 * The video, with the player's own controls: play and pause, seeking, the playback speed from 0.5× to
 * 2×, and the subtitle and audio tracks the video has. While it is shown and [pictureInPicture] holds,
 * leaving the app shrinks it into a window of its own, from where it is on screen.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(
    player: Player,
    pictureInPicture: Boolean,
    covered: Boolean,
    onShown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(player) {
        onShown(true)
        onDispose { onShown(false) }
    }
    val activity = LocalActivity.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(activity, pictureInPicture, bounds) { activity?.offerPictureInPicture(pictureInPicture, bounds) }
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                setShowSubtitleButton(true)
                setShowNextButton(false)
                setShowPreviousButton(false)
                keepScreenOn = true
            }
        },
        // while the picture-in-picture window's view has the player this one lets go, then takes it back
        update = { view -> view.player = player.takeUnless { covered } },
        modifier = modifier.fillMaxWidth().aspectRatio(WIDE).background(Color.Black)
            .onGloballyPositioned { layout ->
                val box = layout.boundsInWindow()
                bounds =
                    Rect(box.left.roundToInt(), box.top.roundToInt(), box.right.roundToInt(), box.bottom.roundToInt())
            },
    )
}

/**
 * The video alone, filling the picture-in-picture window over the app, which stays as it was beneath:
 * the window has its own controls.
 */
@OptIn(UnstableApi::class)
@Composable
public fun PictureInPicturePlayer(playback: VideoPlayback, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = playback.player
                useController = false
            }
        },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxSize().background(Color.Black),
    )
}

/**
 * The window a video shrinks into when the reader leaves the app, as the system draws it: the video's
 * shape, from where it is on screen. From Android 12 the system enters it on its own when [wanted];
 * before that the activity asks as the reader leaves (see [pictureInPictureParams]).
 */
private fun Activity.offerPictureInPicture(wanted: Boolean, bounds: Rect?) {
    setPictureInPictureParams(pictureInPictureParams(bounds, autoEnter = wanted))
}

/** The picture-in-picture window's shape and where it grows from; [autoEnter] lets Android 12 and later enter it. */
public fun pictureInPictureParams(bounds: Rect? = null, autoEnter: Boolean = false): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(WIDTH, HEIGHT))
    bounds?.let(builder::setSourceRectHint)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
    return builder.build()
}

private const val WIDTH = 16
private const val HEIGHT = 9
private const val WIDE = 16f / 9f
