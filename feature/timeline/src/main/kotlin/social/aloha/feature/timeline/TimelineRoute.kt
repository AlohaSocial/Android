// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.DeleteRequest
import social.aloha.core.ui.DeleteStatusDialog
import social.aloha.core.ui.LocalActAs
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowUi

@Composable
public fun TimelineRoute(
    navigation: StatusNavigation,
    modifier: Modifier = Modifier,
    feed: TimelineFeed,
    navigationIcon: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
    onSearch: (() -> Unit)? = null,
    toolbar: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onCatchUp: (() -> Unit)? = null,
    allAccounts: Boolean = false,
) {
    val viewModel = timelineModel(feed, allAccounts)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<DeleteRequest?>(null) }
    val colors = RichTextColors.fromTheme()
    val failed = stringResource(UiR.string.status_action_failed)
    val copied = stringResource(UiR.string.status_link_copied)
    val nav by rememberUpdatedState(navigation)

    // the model changes in place between one account and every account: the new one needs the colours too
    LaunchedEffect(viewModel, colors) { viewModel.onColors(colors) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown(isShown = true)
        onPauseOrDispose { viewModel.onShown(isShown = false) }
    }
    var shakes by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            viewModel.onActionFailureShown()
            shakes++
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

    val actAs = LocalActAs.current
    val items by rememberUpdatedState(state.items)
    val shown = remember(rowActions, actAs, feed, viewModel) {
        routed(rowActions, feed, viewModel as? MergedTimelineViewModel) { actingAs ->
            Elsewhere(rowActions, actAs, { nav }, { urlIn(items, it) }, actingAs ?: { actAs?.current })
        }
    }
    val timelineActions = rememberCaughtUp(viewModel, onCatchUp)
    TimelineScreen(
        state,
        timelineActions,
        shown,
        modifier,
        snackbars,
        listState,
        title = titleOf(feed),
        navigationIcon = navigationIcon,
        showOptions = false,
        bar = feed !is TimelineFeed.Pinned,
        // on Home's pages the button is Home's own; the keyboard's new post is the page's
        onCompose = if (feed is TimelineFeed.Pinned) ({ nav.openComposer(null) }) else null,
        onAlbums = if (feed.mode == FeedMode.Photos && state.albums) ({ nav.openAlbums() }) else null,
        onExplore = if (feed.mode == FeedMode.Photos) ({ nav.openPhotoExplore() }) else null,
        onSearch = onSearch,
        toolbar = toolbar,
        header = header,
        onVideo = { nav.openVideoOf(it, items) },
        shake = shakes,
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

@Composable
private fun titleOf(feed: TimelineFeed): String = when (feed) {
    TimelineFeed.Home -> stringResource(R.string.timeline_title)

    is TimelineFeed.Mode -> stringResource(modeTitle(feed.mode))

    is TimelineFeed.Tag -> feed.handle?.let {
        stringResource(R.string.timeline_title_account_tag, feed.name, it)
    } ?: "#${feed.name}"

    is TimelineFeed.List -> feed.title

    // Home's own bar names a pinned feed; its page has none
    is TimelineFeed.Pinned -> ""
}

/** Every account's timeline together where [allAccounts], else [feed] of the account in use. */
@Composable
private fun timelineModel(feed: TimelineFeed, allAccounts: Boolean): TimelineModel = if (allAccounts) {
    hiltViewModel<MergedTimelineViewModel, MergedTimelineViewModel.Factory>(key = "all-$feed") { it.create(feed) }
} else {
    hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(key = feed.toString()) { it.create(feed) }
}

/**
 * What a row's actions do on [feed]: every account's posts each as the account it belongs to holds it,
 * another server's posts [elsewhere], acting as the reader, else as [rowActions] do them.
 */
private fun routed(
    rowActions: StatusActions,
    feed: TimelineFeed,
    merged: MergedTimelineViewModel?,
    elsewhere: (actingAs: ((StatusRowUi) -> String?)?) -> Elsewhere,
): StatusActions = when {
    merged != null -> MergedActions(rowActions, elsewhere { merged.ownerOf(it.statusId) }, merged::heldAs)
    (feed as? TimelineFeed.Pinned)?.source is TimelineSource.Remote -> elsewhere(null)
    else -> rowActions
}

/**
 * The watch page of [statusId]; a video another account than the one in use holds, under its server's
 * name, opens by its address instead, which the server in use finds.
 */
private fun StatusNavigation.openVideoOf(statusId: String, items: List<TimelineItem>) {
    if (statusId.startsWith(
            TimelineSource.Remote.ID_PREFIX,
        )
    ) {
        urlIn(items, statusId)?.let(::openWeb)
    } else {
        openVideo(statusId)
    }
}

/** The address of the post [statusId] among [items]. */
private fun urlIn(items: List<TimelineItem>, statusId: String): String? =
    items.firstNotNullOfOrNull { (it as? TimelineItem.Post)?.row?.takeIf { row -> row.statusId == statusId }?.url }

/** [viewModel]'s actions, the caught-up line opening what arrived since where [onCatchUp] is given. */
@Composable
private fun rememberCaughtUp(viewModel: TimelineModel, onCatchUp: (() -> Unit)?): TimelineScreenActions {
    val catchUp by rememberUpdatedState(onCatchUp)
    return remember(viewModel, onCatchUp != null) {
        if (onCatchUp == null) {
            viewModel
        } else {
            object : TimelineScreenActions by viewModel {
                override fun onCaughtUp() {
                    catchUp?.invoke()
                }
            }
        }
    }
}

internal fun modeTitle(mode: FeedMode): Int = when (mode) {
    FeedMode.Home -> R.string.timeline_title
    FeedMode.Photos -> R.string.timeline_title_photos
    FeedMode.Video -> R.string.timeline_title_video
    FeedMode.Shorts -> R.string.timeline_title_shorts
    FeedMode.News -> R.string.timeline_title_news
    FeedMode.Audio -> R.string.timeline_title_audio
}

/**
 * One hashtag's public posts, read like home, with a way back, and above them a way to follow it; with
 * [accountId], only that account's posts with it, known as [handle], and nothing above them.
 */
@Composable
public fun TagRoute(
    name: String,
    navigation: StatusNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accountId: String? = null,
    handle: String? = null,
) {
    TimelineRoute(
        navigation,
        modifier,
        feed = TimelineFeed.Tag(name, accountId, handle),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.timeline_back)) }
        },
        header = { if (accountId == null) TagHeader(name, onTag = navigation::openTag) },
    )
}
