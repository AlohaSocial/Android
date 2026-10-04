// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.ProvideLinkRouting
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.openLink
import social.aloha.core.ui.sourceName

/**
 * Shorts as a mode: the pager edge to edge, the account button and the choice of source over it. The
 * short at rest plays, looping, muted until the person turns sound on; leaving the app pauses it.
 */
@Composable
public fun ShortsRoute(
    navigation: StatusNavigation,
    accountButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<ShortsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nav by rememberUpdatedState(navigation)
    val playback = remember { ShortsPlayback(context) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    val colors = RichTextColors.fromTheme()
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    val snackbars = remember { SnackbarHostState() }
    val failed = stringResource(UiR.string.status_action_failed)
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            viewModel.onActionFailureShown()
            snackbars.showSnackbar(failed)
        }
    }
    val shorts by rememberUpdatedState(state.shorts)
    // counted from the list as composed, which the pager's pages are drawn from: counted from the newest
    // state, a page arriving between frames is counted before the pager has its shorts
    val pagerState = rememberPagerState { shorts.size }
    val muted by rememberUpdatedState(state.muted)
    LaunchedEffect(state.shorts) { playback.setItems(state.shorts.map { it.source }) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page -> playback.show(page, muted) }
    }
    // a page that arrives may still leave the reader near the end, as a first page of one short does
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to shorts.size }.distinctUntilChanged().collect { (page, count) ->
            if (count > 0 && page >= count - NEAR_END) viewModel.onNearEnd()
        }
    }
    LaunchedEffect(state.shorts.isNotEmpty()) {
        if (state.shorts.isNotEmpty()) playback.show(pagerState.settledPage, muted)
    }
    LaunchedEffect(state.muted) { playback.setMuted(state.muted) }
    LaunchedEffect(state.loop) { playback.setLoop(state.loop) }
    val actions = remember(viewModel) {
        object : ShortsActions {
            override fun onFavourite(short: ShortUi) = viewModel.onToggle(short.row.statusId, Toggle.Favourite)

            override fun onBoost(short: ShortUi) = viewModel.onToggle(short.row.statusId, Toggle.Boost)

            override fun onComments(short: ShortUi) = nav.openThread(short.row.statusId)

            override fun onShare(short: ShortUi) {
                val url = short.row.url ?: return
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                context.startActivity(Intent.createChooser(send, null))
            }

            override fun onProfile(short: ShortUi) = nav.openProfile(short.row.author.id, null)

            override fun onMuted(muted: Boolean) = viewModel.onMuted(muted)

            override fun onReport(short: ShortUi) =
                nav.report(short.row.author.id, short.row.author.handle, short.row.statusId)
        }
    }
    val title = stringResource(R.string.shorts_title)
    Box(modifier.fillMaxSize().semantics { paneTitle = title }) {
        when {
            state.shorts.isNotEmpty() -> ProvideLinkRouting(onLink = { nav.openLink(it) }) {
                ShortsPager(state, pagerState, playback.player, actions)
            }

            state.loadedOnce -> Text(
                stringResource(R.string.shorts_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(AlohaSpacing.l),
            )

            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        // the tabs read on a bright frame as on a dark one
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM), Color.Transparent)))
                .statusBarsPadding().padding(AlohaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            accountButton()
            state.sources.takeIf { it.size > 1 }?.forEach { source ->
                SourceTab(source, selected = source == state.source) { viewModel.onSource(source) }
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SourceTab(source: TimelineSource, selected: Boolean, onSelect: () -> Unit) {
    val name = stringResource(sourceName(source))
    TextButton(onClick = onSelect, modifier = Modifier.semantics { this.selected = selected }) {
        Text(
            name,
            color = if (selected) Color.White else Color.White.copy(alpha = UNSELECTED),
            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val NEAR_END = 3
private const val UNSELECTED = 0.7f
private const val SCRIM = 0.5f
