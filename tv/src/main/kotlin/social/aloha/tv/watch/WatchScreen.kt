// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.watch

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import social.aloha.core.model.VideoChapter
import social.aloha.core.model.VideoChapters
import social.aloha.tv.R
import social.aloha.tv.TvInsets
import social.aloha.tv.tap

@Composable
internal fun WatchRoute(
    statusId: String,
    viewModel: TvWatchViewModel = hiltViewModel<TvWatchViewModel, TvWatchViewModel.Factory> { it.create(statusId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        WatchActions(
            viewModel::onReveal,
            viewModel::onSeek,
            viewModel::onFollow,
            viewModel::onReport,
            viewModel::onRetry,
        )
    }
    WatchScreen(state, viewModel.player, actions)
}

/**
 * The video fills the screen, its own controls on the D-pad: centre plays and pauses, left and right
 * seek along the time bar. Down leaves the player for the video's details: follow, report, its chapters
 * to jump to, what it says and its comments to read.
 */
@Composable
internal fun WatchScreen(state: WatchUiState, player: Player, actions: WatchActions) {
    val playerFocus = remember { FocusRequester() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TvInsets.vertical)) {
        item {
            Box(Modifier.fillParentMaxHeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    state.failed -> Failed(actions.onRetry, playerFocus)

                    state.covered -> Covered(state.warning, actions.onReveal, playerFocus)

                    else -> PlayerSurface(
                        player,
                        controls = true,
                        Modifier.fillMaxSize().focusRequester(playerFocus),
                    )
                }
            }
        }
        item { Details(state, actions) }
        if (state.chapters.isNotEmpty()) item { Chapters(state.chapters, actions.onSeek) }
        items(state.comments, key = { it.id }) { Comment(it) }
    }
    LaunchedEffect(state.loading, state.covered, state.failed) { if (!state.loading) playerFocus.requestFocus() }
    // nothing plays behind the launcher once the app goes to the background
    LifecycleStartEffect(player) { onStopOrDispose { player.pause() } }
}

/** A comment to read: it takes focus so the D-pad scrolls through them, and shows it with a border. */
@Composable
private fun Comment(comment: CommentUi) {
    var focused by remember { mutableStateOf(false) }
    val border = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        Modifier.padding(horizontal = TvInsets.horizontal, vertical = 4.dp).widthIn(max = TEXT_WIDTH)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics(mergeDescendants = true) {}
            .border(2.dp, border, MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Text(comment.author, style = MaterialTheme.typography.titleSmall)
        Text(comment.text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Covered(warning: String, onReveal: () -> Unit, focus: FocusRequester) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.tv_sensitive_video), style = MaterialTheme.typography.headlineSmall)
        if (warning.isNotBlank()) Text(warning, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onReveal, modifier = Modifier.focusRequester(focus).tap(onReveal)) {
            Text(stringResource(R.string.tv_watch_anyway))
        }
    }
}

@Composable
private fun Failed(onRetry: () -> Unit, focus: FocusRequester) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.tv_load_failed), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onRetry, modifier = Modifier.focusRequester(focus).tap(onRetry)) {
            Text(stringResource(R.string.tv_retry))
        }
    }
}

@Composable
private fun Details(state: WatchUiState, actions: WatchActions) {
    var reporting by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = TvInsets.horizontal, vertical = TvInsets.vertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(state.author, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            state.following?.let { following ->
                Button(onClick = actions.onFollow, modifier = Modifier.tap(actions.onFollow)) {
                    Text(stringResource(if (following) R.string.tv_unfollow else R.string.tv_follow))
                }
            }
            val report = { if (!state.reported) reporting = true }
            OutlinedButton(onClick = report, enabled = !state.reported, modifier = Modifier.tap(report)) {
                Text(stringResource(if (state.reported) R.string.tv_reported else R.string.tv_report))
            }
        }
        if (state.actionFailed) {
            Text(
                stringResource(R.string.tv_action_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.description.isNotBlank()) {
            Text(
                state.description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(max = TEXT_WIDTH),
            )
        }
    }
    if (reporting) {
        ReportDialog(
            onReason = {
                reporting = false
                actions.onReport(it)
            },
            onDismiss = { reporting = false },
        )
    }
}

@Composable
private fun Chapters(chapters: List<VideoChapter>, onSeek: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.tv_chapters),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = TvInsets.horizontal).semantics { heading() },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvInsets.horizontal),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(chapters, key = { it.start }) { chapter ->
                Card(onClick = { onSeek(chapter.start) }, modifier = Modifier.tap { onSeek(chapter.start) }) {
                    Column(Modifier.padding(16.dp).widthIn(max = CHAPTER_WIDTH)) {
                        Text(VideoChapters.clock(chapter.start), style = MaterialTheme.typography.labelLarge)
                        Text(chapter.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** What the report is about, the three kinds that ask nothing further of a reader holding a remote. */
@Composable
private fun ReportDialog(onReason: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.tv_report_title), style = MaterialTheme.typography.headlineSmall)
                ReportReason.entries.forEachIndexed { index, reason ->
                    val modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
                    OutlinedButton(onClick = { onReason(reason) }, modifier = modifier.tap { onReason(reason) }) {
                        Text(stringResource(reason.label()))
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.tap(onDismiss)) {
                    Text(stringResource(R.string.tv_cancel))
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}

private fun ReportReason.label(): Int = when (this) {
    ReportReason.Spam -> R.string.tv_report_spam
    ReportReason.Legal -> R.string.tv_report_legal
    ReportReason.Other -> R.string.tv_report_other
}

private val TEXT_WIDTH = 960.dp
private val CHAPTER_WIDTH = 220.dp
