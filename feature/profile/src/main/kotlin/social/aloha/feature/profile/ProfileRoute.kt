// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

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
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.PeopleKind
import social.aloha.core.ui.DeleteRequest
import social.aloha.core.ui.DeleteStatusDialog
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.openInBrowser

@Composable
public fun ProfileRoute(key: AccountKey, navigation: ProfileNavigation, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(key = key.toString()) { it.create(key) }
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
            override fun onBoost(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Boost)

            override fun onFavourite(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Favourite)

            override fun onBookmark(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Bookmark)

            override fun onMute(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.MuteConversation)

            override fun onPin(row: StatusRowUi) = viewModel.onToggle(row.statusId, Toggle.Pin)

            override fun onVote(row: StatusRowUi, choices: List<Int>) = viewModel.onVote(row.statusId, choices)
        }
    }

    val screenActions = remember(viewModel, context) {
        object : ProfileScreenActions, ProfileActions by viewModel {
            override fun onBack() = nav.back()

            override fun onPeople(followers: Boolean) {
                val id = state.header?.author?.id ?: return
                nav.openPeople(id, if (followers) PeopleKind.Followers else PeopleKind.Following)
            }

            override fun onOpenInBrowser(url: String) = openInBrowser(context, url)

            override fun onEditProfile() = nav.editProfile()

            override fun onReport() {
                val author = state.header?.author ?: return
                nav.report(author.id, author.handle, statusId = null)
            }
        }
    }
    // the reader's own profile shows what they just changed when they come back to it
    LifecycleResumeEffect(state.header?.isSelf) {
        if (state.header?.isSelf == true) viewModel.onRefresh()
        onPauseOrDispose {}
    }

    ProfileScreen(state, screenActions, rowActions, modifier, snackbars)

    deleting?.let { request ->
        DeleteStatusDialog(
            request,
            onDelete = viewModel::onDelete,
            onRedraft = { nav.editPost(it, redraft = true) },
            onDismiss = { deleting = null },
        )
    }
}
