// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.ui.DeleteRequest
import social.aloha.core.ui.DeleteStatusDialog
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusRowUi

@Composable
public fun ThreadRoute(key: ThreadKey, navigation: ThreadNavigation, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(key = key.toString()) { it.create(key) }
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
    val archived = stringResource(R.string.thread_archived)
    LaunchedEffect(state.archived) {
        if (state.archived) {
            viewModel.onNoticeShown(archived = true)
            snackbars.showSnackbar(archived)
        }
    }
    var shakes by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            shakes++
            viewModel.onNoticeShown(archived = false)
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
            archive = { state.archive },
        ) {
            // the focused post is already open: a link to it shakes the list instead
            override fun onOpen(statusId: String) {
                if (statusId != key.statusId) super.onOpen(statusId) else shakes++
            }

            override fun onHistory(row: StatusRowUi) {
                if (row.statusId == key.statusId) viewModel.onHistory(open = true) else super.onHistory(row)
            }

            override fun onReply(row: StatusRowUi) = viewModel.replies.onReply(row.statusId)

            override fun onBoost(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Boost)

            override fun onFavourite(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Favourite)

            override fun onBookmark(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Bookmark)

            override fun onMute(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.MuteConversation)

            override fun onPin(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Pin)

            override fun onArchive(row: StatusRowUi) = viewModel.onArchive(row.statusId)

            override fun onVote(row: StatusRowUi, choices: List<Int>) = viewModel.onVote(row.statusId, choices)

            // only the focused post has its reactions fetched, so only it offers them
            override fun onReact(row: StatusRowUi, name: String, add: Boolean) = viewModel.onReact(name, add)
        }
    }

    val screenActions = remember(viewModel) {
        object : ThreadScreenActions {
            override fun onBack() = nav.back()

            override fun onRefresh() = viewModel.onRefresh()

            override fun onMore(parentId: String) = nav.openThread(parentId)

            override fun onList(kind: StatusListKind) = nav.openList(key.statusId, kind)

            override fun onHistory() = viewModel.onHistory(open = true)

            override fun onHistoryDismissed() = viewModel.onHistory(open = false)
        }
    }

    val found = stringResource(R.string.thread_more_replies_found)
    val show = stringResource(R.string.thread_more_replies_show)
    // asked again as more arrive, and not for ever: an endless one held back every other notice
    LaunchedEffect(state.pendingReplies) {
        if (state.pendingReplies > 0) {
            val answer = snackbars.showSnackbar(found, actionLabel = show, duration = SnackbarDuration.Long)
            if (answer == SnackbarResult.ActionPerformed) viewModel.onShowReplies()
        }
    }
    LaunchedEffect(state.replyTo) {
        state.replyTo?.let {
            nav.openComposer(it)
            viewModel.replies.onReplyOpened()
        }
    }

    val summaries = hiltViewModel<SummaryViewModel>(key = "summary-$key")
    val summarising by summaries.offered.collectAsStateWithLifecycle()
    val summary by summaries.summary.collectAsStateWithLifecycle()
    val translating = rememberThreadTranslation(state.items)
    ThreadScreen(state, screenActions, rowActions, modifier, snackbars, shake = shakes) {
        ThreadMenu(
            listOfNotNull(
                translating,
                (R.string.thread_summarise to { summaries.summarise(state.items) }).takeIf { summarising },
            ),
        )
    }
    summary?.let { SummarySheet(it, summaries::dismiss) }
    state.nudge?.let { NudgeSheet(it, viewModel.replies::onNudged) }

    deleting?.let { request ->
        DeleteStatusDialog(
            request,
            onDelete = viewModel::onDelete,
            onRedraft = { nav.editPost(it, redraft = true) },
            onDismiss = { deleting = null },
        )
    }
}
