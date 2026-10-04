// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.launch
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.VideoChapter
import social.aloha.core.model.VideoChapters
import social.aloha.core.navigation.WatchKey
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.minuteTicks
import social.aloha.core.ui.videoTitle

@Composable
public fun WatchRoute(key: WatchKey, navigation: StatusNavigation, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<WatchViewModel, WatchViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val colors = RichTextColors.fromTheme()
    val failed = stringResource(UiR.string.status_action_failed)
    val copied = stringResource(UiR.string.status_link_copied)
    val nav by rememberUpdatedState(navigation)
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            viewModel.onActionFailureShown()
            snackbars.showSnackbar(failed)
        }
    }
    val rowActions = remember(viewModel, context) {
        object : RoutedStatusActions(
            context,
            navigation = { nav },
            onCopied = { scope.launch { snackbars.showSnackbar(copied) } },
            // a comment is deleted where it is read in full, its thread
            onDeleteAsked = { nav.openThread(it.row.statusId) },
        ) {
            override fun onBoost(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Boost)

            override fun onFavourite(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Favourite)

            override fun onBookmark(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Bookmark)

            override fun onMute(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.MuteConversation)

            override fun onPin(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Pin)

            override fun onVote(row: StatusRowUi, choices: List<Int>) = nav.openThread(row.statusId)
        }
    }
    val pictureInPicture by viewModel.pictureInPicture.collectAsStateWithLifecycle()
    val covered by viewModel.inPictureInPicture.collectAsStateWithLifecycle()
    val now by remember { minuteTicks(Clock.systemUTC()) }.collectAsStateWithLifecycle(Instant.now())
    WatchScreen(
        state,
        now,
        WatchScreenActions(
            onBack = onBack,
            onFollow = viewModel::onFollow,
            onProfile = { state.video?.let { nav.openProfile(it.author.id, null) } },
            onComment = { nav.openComposer(key.statusId) },
            onChapter = { viewModel.onSeek(it.start) },
            onRetry = viewModel::onRetry,
        ),
        rowActions,
        snackbars,
        modifier,
    ) {
        VideoPlayer(viewModel.player, pictureInPicture, covered, onShown = viewModel::onShown)
    }
}

/** What the watch page asks for beyond a post's own actions. */
internal class WatchScreenActions(
    val onBack: () -> Unit,
    val onFollow: () -> Unit,
    val onProfile: () -> Unit,
    val onComment: () -> Unit,
    val onChapter: (VideoChapter) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The watch page: the video, its title, who posted it with the way to follow them, how often it was
 * watched and liked (dislikes are shown, never given: the server takes none), its description and
 * chapters, then its comments, each an ordinary reply. On a foldable half open like a laptop the video
 * keeps the upper half to itself and the rest scrolls below the fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchScreen(
    state: WatchUiState,
    now: Instant,
    actions: WatchScreenActions,
    rowActions: RoutedStatusActions,
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
    tabletop: Boolean = currentWindowAdaptiveInfoV2().windowPosture.isTabletop,
    player: @Composable () -> Unit,
) {
    val post = state.video
    val title = post?.let { videoTitle(it) } ?: stringResource(R.string.watch_title)
    val split = tabletop && state.sources.isNotEmpty()
    // moved rather than rebuilt when the fold changes the layout, as far as Compose can move it into the list
    val current by rememberUpdatedState(player)
    val video = remember { movableContentOf { current() } }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watch_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(AlohaIcons.Back, stringResource(R.string.watch_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        if (split) {
            // ponytail: an even split puts the fold near enough the middle; the hinge's own bounds
            // (windowPosture.hingeList) if a device's fold sits elsewhere
            Column(Modifier.padding(padding).fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { video() }
                Details(state, post, title, now, actions, rowActions, Modifier.weight(1f), player = null)
            }
        } else {
            Details(state, post, title, now, actions, rowActions, Modifier.padding(padding).fillMaxSize(), video)
        }
    }
}

/** Everything under the video, with the video atop it unless it sits apart ([player] null). */
@Composable
private fun Details(
    state: WatchUiState,
    video: StatusRowUi?,
    title: String,
    now: Instant,
    actions: WatchScreenActions,
    rowActions: RoutedStatusActions,
    modifier: Modifier,
    player: (@Composable () -> Unit)?,
) {
    LazyColumn(modifier) {
        if (player != null) item(key = "player") { if (state.sources.isNotEmpty()) player() }
        when {
            video != null -> {
                item(key = "about") { About(video, title, state.following, actions) }
                if (state.chapters.isNotEmpty()) {
                    item(key = "chapters") {
                        Chapters(state.chapters, actions.onChapter)
                    }
                }
                item(key = "comments") { CommentsHeading(video, actions.onComment) }
                items(state.comments, key = { it.rowId }) { comment ->
                    StatusCard(comment, now, LocalSensitiveMediaPolicy.current, rowActions)
                    PostDivider()
                }
            }

            state.failed -> item(key = "failed") {
                Column(
                    Modifier.fillMaxWidth().padding(AlohaSpacing.l),
                    verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.watch_failed), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = actions.onRetry) { Text(stringResource(R.string.watch_retry)) }
                }
            }

            else -> item(key = "loading") { ListProgress() }
        }
    }
}

@Composable
private fun About(video: StatusRowUi, title: String, following: Boolean?, actions: WatchScreenActions) {
    Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Counts(video)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.onProfile, modifier = Modifier.weight(1f)) {
                Text(video.author.plainName, maxLines = 1, modifier = Modifier.fillMaxWidth())
            }
            following?.let {
                // following who posted a video is subscribing to their channel, and reads as such
                if (it) {
                    OutlinedButton(onClick = actions.onFollow) { Text(stringResource(R.string.watch_unfollow)) }
                } else {
                    Button(onClick = actions.onFollow) { Text(stringResource(R.string.watch_follow)) }
                }
            }
        }
        if (video.plainText.isNotBlank()) Text(video.body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Counts(video: StatusRowUi) {
    val details = video.video ?: return
    if (!LocalReadingStyle.current.showCounts) return
    val parts = listOfNotNull(
        details.views.takeIf { it > 0 }?.let { pluralStringResource(UiR.plurals.video_views, it, it) },
        details.likes.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.watch_likes, it, it) },
        details.dislikes.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.watch_dislikes, it, it) },
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Chapters(chapters: List<VideoChapter>, onChapter: (VideoChapter) -> Unit) {
    Column(Modifier.padding(horizontal = AlohaSpacing.m)) {
        Text(
            stringResource(R.string.watch_chapters),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        chapters.forEach { chapter ->
            val clock = VideoChapters.clock(chapter.start)
            val label = stringResource(R.string.watch_chapter, chapter.title, clock)
            TextButton(onClick = { onChapter(chapter) }, modifier = Modifier.semantics { contentDescription = label }) {
                Text("$clock  ${chapter.title}", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CommentsHeading(video: StatusRowUi, onComment: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        val count = video.counts.replies
        Text(
            pluralStringResource(R.plurals.watch_comments, count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onComment) { Text(stringResource(R.string.watch_comment)) }
    }
}
