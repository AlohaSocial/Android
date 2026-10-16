// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.watch

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * [player]'s picture, with its own controls when [controls], which take the D-pad: centre plays and
 * pauses, left and right seek along the time bar. Without them the picture fills its frame, as a preview
 * or a short does. The screen stays on while it shows.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerSurface(player: Player, controls: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = controls
                if (controls) {
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                } else {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                keepScreenOn = true
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
