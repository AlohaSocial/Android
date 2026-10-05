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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.AdminAccount
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.model.AdminLink
import social.aloha.core.model.AdminReport
import social.aloha.core.model.AdminTag
import social.aloha.core.model.Status
import social.aloha.core.navigation.ModerationKey
import social.aloha.core.network.endpoints.AdminAccountEndpoints.Origin
import social.aloha.core.network.endpoints.AdminAccountEndpoints.Standing
import social.aloha.core.network.endpoints.ModerationEndpoints.TrendKind
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.Skeleton
import social.aloha.core.ui.openInBrowser
import social.aloha.core.ui.readingColumn

@Composable
internal fun Reports(
    state: ModerationState,
    actions: ModerationActions,
    confirm: (String, AdminAccountAction, () -> Unit) -> Unit,
) {
    LazyColumn(Modifier.readingColumn().fillMaxSize()) {
        item {
            Chips(
                listOf(R.string.moderation_reports_open to false, R.string.moderation_reports_resolved to true),
                state.resolvedShown,
                actions.onShowResolved,
            )
        }
        val none =
            if (state.resolvedShown) R.string.moderation_reports_none_resolved else R.string.moderation_reports_none
        rows(state.reports, state.failed, none) { report ->
            Report(report, state.resolvedShown, actions, confirm)
        }
    }
}

@Composable
private fun Report(
    report: AdminReport,
    resolved: Boolean,
    actions: ModerationActions,
    confirm: (String, AdminAccountAction, () -> Unit) -> Unit,
) {
    val target = report.targetAccount
    val handle = target?.qualifiedHandle.orEmpty()
    ListItem(
        leadingContent = { Avatar(target?.avatar, AVATAR) },
        headlineContent = { Text(target?.bestDisplayName?.ifBlank { handle } ?: handle) },
        supportingContent = { ReportDetails(report, handle) },
    )
    FlowRow(Modifier.padding(horizontal = AlohaSpacing.m)) {
        if (!resolved) {
            TextButton(onClick = { actions.onAssign(report.id, !report.isAssigned) }) {
                val take =
                    if (report.isAssigned) R.string.moderation_report_give_back else R.string.moderation_report_take
                Text(stringResource(take))
            }
        }
        TextButton(onClick = { actions.onResolve(report.id, !resolved) }) {
            Text(
                stringResource(if (resolved) R.string.moderation_report_reopen else R.string.moderation_report_resolve),
            )
        }
        if (!resolved && target != null) {
            AccountActions { action -> confirm(handle, action) { actions.onActOnReport(report, action) } }
        }
    }
    HorizontalDivider()
}

/** Who it is about, why, and who sent and took it. */
@Composable
private fun ReportDetails(report: AdminReport, handle: String) {
    Column {
        Text(handle)
        val posts = report.statuses.size
        val details = listOfNotNull(
            report.category.takeIf { it.isNotBlank() },
            pluralStringResource(R.plurals.moderation_report_posts, posts, posts).takeIf { posts > 0 },
        )
        if (details.isNotEmpty()) Text(details.joinToString(" · "))
        report.statuses.take(REPORTED_POSTS).forEach { post ->
            Text(
                stringResource(R.string.moderation_report_post, StatusHtmlParser.plainText(post.content)),
                maxLines = COMMENT_LINES,
                overflow = TextOverflow.Ellipsis,
                fontStyle = FontStyle.Italic,
            )
        }
        if (report.comment.isNotBlank()) {
            Text(report.comment, maxLines = COMMENT_LINES, overflow = TextOverflow.Ellipsis)
        }
        report.account?.let { Text(stringResource(R.string.moderation_report_by, it.qualifiedHandle)) }
        report.assignedAccount?.let { Text(stringResource(R.string.moderation_report_taken, it.qualifiedHandle)) }
    }
}

@Composable
private fun AccountActions(onAction: (AdminAccountAction) -> Unit) {
    TextButton(onClick = { onAction(AdminAccountAction.Silence) }) { Text(stringResource(R.string.moderation_limit)) }
    TextButton(onClick = { onAction(AdminAccountAction.Suspend) }) {
        Text(stringResource(R.string.moderation_suspend), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun Accounts(
    state: ModerationState,
    actions: ModerationActions,
    confirm: (String, AdminAccountAction, () -> Unit) -> Unit,
) {
    LazyColumn(Modifier.readingColumn().fillMaxSize()) {
        item { FindAccounts(state, actions.onFindAccounts) }
        rows(state.accounts, state.failed, R.string.moderation_accounts_none) { account ->
            val handle = account.handle
            ListItem(
                leadingContent = { Avatar(account.account?.avatar, AVATAR) },
                headlineContent = { Text(account.account?.bestDisplayName?.ifBlank { handle } ?: handle) },
                supportingContent = { Text(handle) },
            )
            Row(Modifier.padding(horizontal = AlohaSpacing.m)) {
                when {
                    account.suspended -> TextButton(onClick = { actions.onLift(account) }) {
                        Text(stringResource(R.string.moderation_lift_suspension))
                    }

                    account.silenced -> TextButton(onClick = { actions.onLift(account) }) {
                        Text(stringResource(R.string.moderation_lift_limit))
                    }

                    else -> AccountActions { action ->
                        confirm(handle, action) { actions.onAct(account.id, action) }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** The username field, then where the accounts are and their standing. */
@Composable
private fun FindAccounts(state: ModerationState, onFind: (Standing, Origin, String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf(state.username) }
    OutlinedTextField(
        value = typed,
        onValueChange = {
            typed = it
            // emptied, the list is every account again at once
            if (it.isEmpty()) onFind(state.standing, state.origin, "")
        },
        label = { Text(stringResource(R.string.moderation_accounts_find)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onFind(state.standing, state.origin, typed) }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m),
    )
    Chips(
        listOf(
            R.string.moderation_accounts_anywhere to Origin.Any,
            R.string.moderation_accounts_local to Origin.Local,
            R.string.moderation_accounts_remote to Origin.Remote,
        ),
        state.origin,
    ) { onFind(state.standing, it, typed) }
    Chips(
        listOf(
            R.string.moderation_accounts_active to Standing.Active,
            R.string.moderation_accounts_limited to Standing.Silenced,
            R.string.moderation_accounts_suspended to Standing.Suspended,
        ),
        state.standing,
    ) { onFind(it, state.origin, typed) }
}

@Composable
private fun <T> Chips(choices: List<Pair<Int, T>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(Modifier.padding(horizontal = AlohaSpacing.m)) {
        choices.forEach { (label, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.padding(end = AlohaSpacing.s),
            )
        }
    }
}

/** A list's rows, or what stands in their place while it is loading, failed or empty. */
internal fun <T> LazyListScope.rows(list: List<T>?, failed: Boolean, none: Int, row: @Composable (T) -> Unit) {
    when {
        list == null && failed -> item { Note(stringResource(R.string.moderation_failed)) }
        list == null -> item { Loading() }
        list.isEmpty() -> item { Note(stringResource(none)) }
        else -> items(list) { row(it) }
    }
}

@Composable
internal fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m).semantics { heading() },
    )
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.l),
    )
}

@Composable
internal fun Loading() = Skeleton(rows = LOADING_ROWS, avatar = AVATAR)

internal val AVATAR = 40.dp
private const val LOADING_ROWS = 3
internal const val COMMENT_LINES = 3

/** Of the posts a report points at, the first few show; the count says how many there are. */
private const val REPORTED_POSTS = 3
