// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.NotificationKind
import social.aloha.core.ui.AccountRow
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowMapper

/**
 * The notifications tab. A row opens its post, or the profile of whoever did it; a group's "others"
 * lists everyone in it. The filtering rules and the filtered senders open from the top bar where the
 * server has them.
 */
@Composable
public fun NotificationsRoute(
    navigation: StatusNavigation,
    onPolicy: () -> Unit,
    onRequests: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
) {
    val viewModel: NotificationsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown(isShown = true)
        onPauseOrDispose { viewModel.onShown(isShown = false) }
    }
    val actions = remember(viewModel, navigation) {
        object : NotificationsActions {
            override fun onRefresh() = viewModel.onRefresh()

            override fun onKind(kind: NotificationKind) = viewModel.onKind(kind)

            override fun onAllKinds() = viewModel.onAllKinds()

            override fun onNearEnd() = viewModel.onNearEnd()

            override fun onOpen(row: NotificationRowUi) {
                row.statusId?.let(navigation::openThread) ?: row.accountId?.let { navigation.openProfile(it, null) }
            }

            override fun onOthers(groupKey: String) = viewModel.onOthers(groupKey)

            override fun onPolicy() = onPolicy()

            override fun onRequests() = onRequests()

            override fun onAskedForPermission() = viewModel.onAskedForPermission()
        }
    }
    NotificationsScreen(state, actions, navigationIcon, modifier)
    state.group?.let { group ->
        GroupAccountsSheet(group, onOpen = { navigation.openProfile(it, null) }, onClose = viewModel::onGroupClosed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupAccountsSheet(group: GroupSheet, onOpen: (String) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Text(
            stringResource(R.string.notifications_group_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m).semantics { heading() },
        )
        when {
            group.failed -> Text(
                stringResource(R.string.notifications_group_error),
                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m),
            )

            group.accounts == null -> ListProgress()

            else -> GroupAccounts(group.accounts, onOpen)
        }
    }
}

@Composable
private fun GroupAccounts(accounts: List<Account>, onOpen: (String) -> Unit) {
    val colors = RichTextColors.fromTheme()
    val mapper = remember(colors) { StatusRowMapper(RichTextCache(), colors) }
    LazyColumn {
        items(accounts, key = { it.id }) { account ->
            AccountRow(mapper.author(account), account.emojis, onOpen = { onOpen(account.id) })
        }
    }
}
