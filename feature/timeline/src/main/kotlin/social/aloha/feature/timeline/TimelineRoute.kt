// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.FeedMode
import social.aloha.core.ui.DeleteRequest
import social.aloha.core.ui.DeleteStatusDialog
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowUi

@Composable
public fun TimelineRoute(
    navigation: StatusNavigation,
    modifier: Modifier = Modifier,
    feed: TimelineFeed = TimelineFeed.Home,
    navigationIcon: @Composable () -> Unit = {},
) {
    val viewModel =
        hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(key = feed.toString()) { it.create(feed) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<DeleteRequest?>(null) }
    val colors = RichTextColors.fromTheme()
    val failed = stringResource(UiR.string.status_action_failed)
    val copied = stringResource(UiR.string.status_link_copied)
    val nav by rememberUpdatedState(navigation)

    LaunchedEffect(colors) { viewModel.onColors(colors) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown(isShown = true)
        onPauseOrDispose { viewModel.onShown(isShown = false) }
    }
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
            onDeleteAsked = { deleting = it },
            albums = { state.albums },
        ) {
            override fun onBoost(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Boost)

            override fun onFavourite(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Favourite)

            override fun onBookmark(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Bookmark)

            override fun onMute(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.MuteConversation)

            override fun onPin(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Pin)

            override fun onVote(row: StatusRowUi, choices: List<Int>) = viewModel.onVote(row.statusId, choices)
        }
    }

    TimelineScreen(
        state,
        viewModel,
        rowActions,
        modifier,
        snackbars,
        title = when (feed) {
            TimelineFeed.Home -> stringResource(R.string.timeline_title)
            is TimelineFeed.Mode -> stringResource(modeTitle(feed.mode))
            is TimelineFeed.Tag -> "#${feed.name}"
        },
        navigationIcon = navigationIcon,
        showOptions = feed == TimelineFeed.Home,
        onCompose = if (feed == TimelineFeed.Home) ({ nav.openComposer(null) }) else null,
        onAlbums = if (feed.mode == FeedMode.Photos && state.albums) ({ nav.openAlbums() }) else null,
        onExplore = if (feed.mode == FeedMode.Photos) ({ nav.openPhotoExplore() }) else null,
    )

    deleting?.let { request ->
        DeleteStatusDialog(
            request,
            onDelete = viewModel::onDelete,
            onRedraft = { nav.editPost(it, redraft = true) },
            onDismiss = { deleting = null },
        )
    }
}

private fun modeTitle(mode: FeedMode): Int = when (mode) {
    FeedMode.Home -> R.string.timeline_title
    FeedMode.Photos -> R.string.timeline_title_photos
    FeedMode.Video -> R.string.timeline_title_video
    FeedMode.Shorts -> R.string.timeline_title_shorts
    FeedMode.News -> R.string.timeline_title_news
    FeedMode.Audio -> R.string.timeline_title_audio
}

/** One hashtag's public posts, read like home, with a way back. */
@Composable
public fun TagRoute(name: String, navigation: StatusNavigation, onBack: () -> Unit, modifier: Modifier = Modifier) {
    TimelineRoute(
        navigation,
        modifier,
        feed = TimelineFeed.Tag(name),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.timeline_back)) }
        },
    )
}
