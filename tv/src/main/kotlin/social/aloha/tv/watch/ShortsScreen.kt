// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import social.aloha.core.media.ShortsPlayback
import social.aloha.tv.R
import social.aloha.tv.TvInsets

@Composable
internal fun ShortsRoute(startId: String, viewModel: TvShortsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShortsScreen(state, startId, viewModel::onReveal, viewModel::onNearEnd)
}

/**
 * Shorts, one per screen, as a pager the D-pad drives: down for the next, up for the one before, centre to
 * pause and play again, or to show a covered one. One player plays the short in view, the next ones loaded
 * ahead of it, so moving on starts at once.
 */
@Composable
internal fun ShortsScreen(state: TvShortsUiState, startId: String, onReveal: (String) -> Unit, onNearEnd: () -> Unit) {
    val playback = rememberShortsPlayback()
    val pager = rememberPagerState { state.shorts.size }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val current = state.shorts.getOrNull(pager.currentPage)
    val showing = current != null && !current.waits(state.revealed)
    Playing(state, startId, pager, playback, showing, onNearEnd)
    if (state.loadedOnce && state.shorts.isEmpty()) return NoShorts()
    val keys = ShortsKeys(
        move = { by ->
            val target = pager.currentPage + by
            (target in state.shorts.indices).also { if (it) scope.launch { pager.animateScrollToPage(target) } }
        },
        press = { if (current != null) press(current, showing, playback?.player, onReveal) },
    )
    VerticalPager(
        state = pager,
        key = { state.shorts[it].card.id },
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable()
            .onPreviewKeyEvent(keys::onKey),
    ) { page ->
        val short = state.shorts[page]
        val visible = page == pager.currentPage && showing
        Short(short, if (visible) playback?.player else null, covered = short.waits(state.revealed))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * Hands the shorts to [playback] as they come and opens the pager at [startId] once; the pager is keyed by
 * post, so newer shorts arriving above keep the one in view in view. Plays the short in view while it is
 * [showing], again whenever another short comes to that page, and asks for more as the last few come near,
 * again once a page that brought none arrived. Nothing plays once the app goes to the background.
 */
@Composable
private fun Playing(
    state: TvShortsUiState,
    startId: String,
    pager: PagerState,
    playback: ShortsPlayback?,
    showing: Boolean,
    onNearEnd: () -> Unit,
) {
    val shorts = state.shorts
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shorts) {
        playback?.setItems(shorts.map { it.source })
        if (!started && shorts.isNotEmpty()) {
            started = true
            pager.scrollToPage(shorts.indexOfFirst { it.card.id == startId }.coerceAtLeast(0))
        }
    }
    val current = shorts.getOrNull(pager.currentPage)?.card?.id
    LaunchedEffect(current, showing) {
        if (current == null) return@LaunchedEffect
        if (showing) playback?.show(pager.currentPage, muted = false) else playback?.player?.pause()
    }
    LaunchedEffect(pager.currentPage, shorts.size, state.loadingOlder) {
        if (!state.loadingOlder && pager.currentPage >= shorts.size - NEAR_END) onNearEnd()
    }
    LifecycleStartEffect(playback) { onStopOrDispose { playback?.player?.pause() } }
}

@Composable
private fun NoShorts() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.tv_no_shorts), style = MaterialTheme.typography.titleLarge)
    }
}

/** The D-pad on the pager: up and down [move] by one, unless there is no short there; centre [press]es. */
private class ShortsKeys(val move: (Int) -> Boolean, val press: () -> Unit) {
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionDown -> move(1)
            Key.DirectionUp -> move(-1)
            Key.DirectionCenter, Key.Enter -> true.also { press() }
            else -> false
        }
    }
}

/** The shorts' one player, released with the screen; none in a preview, which plays nothing. */
@Composable
private fun rememberShortsPlayback(): ShortsPlayback? {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val playback = remember { if (inspecting) null else ShortsPlayback(context) }
    DisposableEffect(playback) { onDispose { playback?.release() } }
    return playback
}

/** Centre on [short]: shows it when it waits behind its warning, else pauses or plays it again. */
private fun press(short: TvShortUi, showing: Boolean, player: Player?, onReveal: (String) -> Unit) {
    when {
        !showing -> onReveal(short.card.id)
        player?.isPlaying == true -> player.pause()
        else -> player?.play()
    }
}

/** Whether this short waits behind its warning: covered, and not yet asked for. */
private fun TvShortUi.waits(revealed: Set<String>) = card.covered && card.id !in revealed

@Composable
private fun Short(short: TvShortUi, player: Player?, covered: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxHeight().aspectRatio(SHORT_RATIO)) {
            when {
                covered -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.tv_sensitive_video), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.tv_press_to_watch), style = MaterialTheme.typography.bodyLarge)
                }

                player != null -> PlayerSurface(player, controls = false, Modifier.fillMaxSize())

                else -> AsyncImage(
                    model = short.card.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(horizontal = TvInsets.horizontal, vertical = TvInsets.vertical)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${short.card.title}, ${short.card.author}"
                },
        ) {
            Text(short.card.author, style = MaterialTheme.typography.titleMedium)
            Text(short.card.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
        }
    }
}

private const val SHORT_RATIO = 9f / 16
private const val NEAR_END = 3
