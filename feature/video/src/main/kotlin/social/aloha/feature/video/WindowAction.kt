// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

/** What the picture-in-picture window's buttons ask for, as the broadcast each sends names it. */
internal enum class WindowAction(val icon: Int, val title: Int) {
    Back(R.drawable.ic_pip_back, R.string.watch_window_back),
    Play(R.drawable.ic_pip_play, R.string.watch_window_play),
    Pause(R.drawable.ic_pip_pause, R.string.watch_window_pause),
    Forward(R.drawable.ic_pip_forward, R.string.watch_window_forward),
}

/** The window's buttons: back 10 seconds, play or pause as the video is [playing], and forward 10 seconds. */
internal fun windowActions(context: Context, playing: Boolean): List<RemoteAction> = listOf(
    WindowAction.Back,
    if (playing) WindowAction.Pause else WindowAction.Play,
    WindowAction.Forward,
).map { action ->
    val title = context.getString(action.title)
    val intent = Intent(WINDOW_ACTION).setPackage(context.packageName).putExtra(WHICH, action.name)
    val sent = PendingIntent.getBroadcast(
        context,
        action.ordinal,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    RemoteAction(Icon.createWithResource(context, action.icon), title, title, sent)
}

/** Whether [player] plays, as it changes. */
@Composable
internal fun rememberPlaying(player: Player): Boolean {
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return playing
}

/** While composed, the window's buttons do as they say to [player]: only this app can send them. */
@Composable
internal fun WindowButtons(player: Player) {
    val context = LocalContext.current
    DisposableEffect(context, player) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra(WHICH)?.let(WindowAction::valueOf)) {
                    WindowAction.Back -> player.seekTo((player.currentPosition - SKIP_MILLIS).coerceAtLeast(0))
                    WindowAction.Play -> player.play()
                    WindowAction.Pause -> player.pause()
                    WindowAction.Forward -> player.seekTo(player.currentPosition + SKIP_MILLIS)
                    null -> Unit
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WINDOW_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
}

private const val WINDOW_ACTION = "social.aloha.feature.video.WINDOW_ACTION"
private const val WHICH = "which"
private const val SKIP_MILLIS = 10_000L
