// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.moderation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.AdminAccount
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.model.AdminReport
import social.aloha.core.model.AdminTag
import social.aloha.core.model.Status
import social.aloha.core.navigation.ModerationKey
import social.aloha.core.network.endpoints.AdminAccountEndpoints.Origin
import social.aloha.core.network.endpoints.AdminAccountEndpoints.Standing
import social.aloha.core.network.endpoints.ModerationEndpoints.TrendKind
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.openInBrowser
import social.aloha.core.ui.readingColumn

/** The reports queue, the server's accounts and the trends waiting for review, for one moderator. */
@Composable
public fun ModerationRoute(key: ModerationKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ModerationViewModel, ModerationViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.open) {
        state.open?.let {
            openInBrowser(context, it)
            viewModel.onOpened()
        }
    }
    ModerationScreen(
        state,
        ModerationActions(
            onAllow = viewModel::allow,
            onRetry = viewModel::load,
            onShowResolved = viewModel::showResolved,
            onAssign = viewModel::assign,
            onResolve = viewModel::resolve,
            onActOnReport = viewModel::actOnReport,
            onFindAccounts = { standing, origin, username -> viewModel.findAccounts(standing, origin, username) },
            onAct = viewModel::act,
            onLift = viewModel::lift,
            onReview = viewModel::review,
        ),
        viewModel::onRefusalShown,
        onBack,
        modifier,
    )
}

/** What the console changes. */
internal class ModerationActions(
    val onAllow: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShowResolved: (Boolean) -> Unit = {},
    val onAssign: (id: String, mine: Boolean) -> Unit = { _, _ -> },
    val onResolve: (id: String, resolved: Boolean) -> Unit = { _, _ -> },
    val onActOnReport: (AdminReport, AdminAccountAction) -> Unit = { _, _ -> },
    val onFindAccounts: (Standing, Origin, username: String) -> Unit = { _, _, _ -> },
    val onAct: (id: String, AdminAccountAction) -> Unit = { _, _ -> },
    val onLift: (AdminAccount) -> Unit = {},
    val onReview: (TrendKind, id: String, approve: Boolean) -> Unit = { _, _, _ -> },
)

/** An account action waiting for the moderator's yes. */
private class Pending(val handle: String, val action: AdminAccountAction, val go: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModerationScreen(
    state: ModerationState,
    actions: ModerationActions,
    onRefusalShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: ModerationTab = ModerationTab.Reports,
) {
    val title = stringResource(R.string.moderation_title)
    var chosen by rememberSaveable { mutableStateOf(initialTab) }
    val tab = chosen.takeIf { it in state.tabs } ?: state.tabs.firstOrNull()
    var pending by remember { mutableStateOf<Pending?>(null) }
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.moderation_refused)
    LaunchedEffect(state.refused) {
        if (state.refused) {
            snackbars.showSnackbar(refused)
            onRefusalShown()
        }
    }
    val confirm: (String, AdminAccountAction, () -> Unit) -> Unit = { handle, action, go ->
        pending = Pending(handle, action, go)
    }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.moderation_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.tabs.size > 1 && tab != null) {
                // sized to the labels, so a large font scrolls the tabs instead of breaking a word
                PrimaryScrollableTabRow(selectedTabIndex = state.tabs.indexOf(tab), edgePadding = 0.dp) {
                    state.tabs.forEach {
                        Tab(
                            selected = it == tab,
                            onClick = { chosen = it },
                            text = { Text(stringResource(tabTitle(it))) },
                        )
                    }
                }
            }
            when {
                state.role == null && state.failed -> Failed(actions.onRetry)
                state.role == null -> Loading()
                state.consent -> Consent(actions.onAllow)
                tab == ModerationTab.Reports -> Reports(state, actions, confirm)
                tab == ModerationTab.Accounts -> Accounts(state, actions, confirm)
                tab == ModerationTab.Trends -> Trends(state, actions)
            }
        }
    }
    pending?.let { asked -> Confirm(asked) { pending = null } }
}

private fun tabTitle(tab: ModerationTab): Int = when (tab) {
    ModerationTab.Reports -> R.string.moderation_tab_reports
    ModerationTab.Accounts -> R.string.moderation_tab_accounts
    ModerationTab.Trends -> R.string.moderation_tab_trends
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Column(Modifier.readingColumn().padding(AlohaSpacing.l)) {
        Text(stringResource(R.string.moderation_failed), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = AlohaSpacing.m)) {
            Text(stringResource(R.string.moderation_retry))
        }
    }
}

@Composable
private fun Consent(onAllow: () -> Unit) {
    Column(Modifier.readingColumn().padding(AlohaSpacing.l)) {
        Text(stringResource(R.string.moderation_consent), style = MaterialTheme.typography.bodyLarge)
        // never held disabled: a browser tab closed without an answer leaves nothing to wait for
        Button(onClick = onAllow, modifier = Modifier.padding(top = AlohaSpacing.m)) {
            Text(stringResource(R.string.moderation_consent_allow))
        }
    }
}

@Composable
private fun Confirm(asked: Pending, onDone: () -> Unit) {
    val suspend = asked.action == AdminAccountAction.Suspend
    AlertDialog(
        onDismissRequest = onDone,
        title = {
            Text(
                stringResource(
                    if (suspend) R.string.moderation_suspend_title else R.string.moderation_limit_title,
                    asked.handle,
                ),
            )
        },
        text = {
            Text(stringResource(if (suspend) R.string.moderation_suspend_text else R.string.moderation_limit_text))
        },
        confirmButton = {
            TextButton(onClick = {
                asked.go()
                onDone()
            }) { Text(stringResource(if (suspend) R.string.moderation_suspend else R.string.moderation_limit)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.moderation_cancel)) } },
    )
}
