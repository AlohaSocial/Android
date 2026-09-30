// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.navigation.ThreadKey
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
    var deleting by remember { mutableStateOf<StatusRowUi?>(null) }
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
            onDeleteAsked = { deleting = it },
        ) {
            // the focused post is already open; a tap on it does nothing
            override fun onOpen(statusId: String) {
                if (statusId != key.statusId) super.onOpen(statusId)
            }

            override fun onBoost(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Boost)

            override fun onFavourite(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Favourite)

            override fun onBookmark(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Bookmark)

            override fun onMute(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.MuteConversation)

            override fun onPin(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Pin)

            override fun onVote(row: StatusRowUi, choices: List<Int>) = viewModel.onVote(row.statusId, choices)
        }
    }

    val screenActions = remember(viewModel) {
        object : ThreadScreenActions {
            override fun onBack() = nav.back()

            override fun onRefresh() = viewModel.onRefresh()

            override fun onMore(parentId: String) = nav.openThread(parentId)

            override fun onList(kind: StatusListKind) = nav.openList(key.statusId, kind)

            override fun onHistory() = viewModel.onHistory()

            override fun onHistoryDismissed() = viewModel.onHistoryDismissed()
        }
    }

    ThreadScreen(state, screenActions, rowActions, modifier, snackbars)

    deleting?.let { row ->
        DeleteStatusDialog(
            onConfirm = {
                deleting = null
                viewModel.onDelete(row.statusId)
            },
            onDismiss = { deleting = null },
        )
    }
}
