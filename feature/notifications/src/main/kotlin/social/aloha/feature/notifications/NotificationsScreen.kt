// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.badgeCount
import social.aloha.core.model.NotificationKind
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.NearEndEffect
import social.aloha.core.ui.PostAge
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.StackedAvatars
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.readingColumn
import social.aloha.core.ui.short
import social.aloha.core.ui.spoken

/** The chips across the top, each backed by the server's `types` filter. */
private val FILTERS = listOf(
    NotificationKind.Mention to R.string.notifications_filter_mentions,
    NotificationKind.Reblog to R.string.notifications_filter_boosts,
    NotificationKind.Favourite to R.string.notifications_filter_favourites,
    NotificationKind.Follow to R.string.notifications_filter_follows,
    NotificationKind.Poll to R.string.notifications_filter_polls,
    NotificationKind.Update to R.string.notifications_filter_updates,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsScreen(
    state: NotificationsUiState,
    actions: NotificationsActions,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.notifications_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                actions = {
                    IconButton(onClick = actions::onRefresh) {
                        Icon(AlohaIcons.Retry, contentDescription = stringResource(R.string.notifications_refresh))
                    }
                    if (state.filtering) FilteringActions(state.pendingRequests, actions)
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Chips(state.kinds, actions)
            state.trouble?.let { TroubleStrip(stringResource(it.message)) }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions::onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.rows.isNotEmpty() -> Rows(state, actions)
                    state.loadedOnce -> Empty(state.kinds.isNotEmpty())
                    else -> ListProgress()
                }
            }
        }
    }
}

@Composable
private fun FilteringActions(pending: Int, actions: NotificationsActions) {
    val requests = if (pending > 0) {
        pluralStringResource(R.plurals.notifications_requests_action, pending, pending)
    } else {
        stringResource(R.string.notifications_requests_open)
    }
    IconButton(onClick = actions::onRequests) {
        BadgedBox(badge = { if (pending > 0) Badge { Text(badgeCount(pending)) } }) {
            Icon(AlohaIcons.Filtered, contentDescription = requests)
        }
    }
    IconButton(onClick = actions::onPolicy) {
        Icon(AlohaIcons.Rules, contentDescription = stringResource(R.string.notifications_policy_action))
    }
}

@Composable
private fun Chips(kinds: Set<NotificationKind>, actions: NotificationsActions) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AlohaSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        FilterChip(
            selected = kinds.isEmpty(),
            onClick = actions::onAllKinds,
            label = { Text(stringResource(R.string.notifications_filter_all)) },
        )
        FILTERS.forEach { (kind, label) ->
            FilterChip(
                selected = kind in kinds,
                onClick = { actions.onKind(kind) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun Rows(state: NotificationsUiState, actions: NotificationsActions) {
    val listState = rememberLazyListState()
    NearEndEffect(listState, state.rows.size, actions::onNearEnd)
    LazyColumn(
        state = listState,
        modifier = Modifier.readingColumn(),
        contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
    ) {
        items(state.rows, key = { it.key }) { row -> NotificationRow(row, state.now, actions) }
        if (state.loadingOlder) item { ListProgress() }
    }
}

@Composable
internal fun NotificationRow(row: NotificationRowUi, now: Instant, actions: NotificationsActions) {
    val summary = summary(row)
    val age = PostAge.of(row.at, now)
    val spokenAge = age.spoken(stringResource(UiR.string.status_age_now))
    val unread = stringResource(R.string.notifications_unread)
    val description = listOfNotNull(unread.takeIf { row.unread }, summary, row.preview, spokenAge).joinToString(". ")
    val background = if (row.unread) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(role = Role.Button) { actions.onOpen(row) }
            .semantics { contentDescription = description }
            .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
    ) {
        Icon(
            row.kind.icon,
            contentDescription = null,
            tint = row.kind.tint(),
            modifier = Modifier.padding(top = AlohaSpacing.xs).size(KIND_ICON),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.avatars.isNotEmpty()) StackedAvatars(row.avatars, AVATAR)
                Box(Modifier.weight(1f))
                Text(
                    age.short(stringResource(UiR.string.status_age_now)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.unread) FontWeight.SemiBold else null,
            )
            row.preview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.groupKey != null && row.others > 0) {
                TextButton(onClick = { actions.onOthers(row.groupKey) }) {
                    Text(pluralStringResource(R.plurals.notifications_others, row.others, row.others))
                }
            }
        }
    }
}

@Composable
private fun summary(row: NotificationRowUi): String {
    val name = row.name ?: stringResource(R.string.notifications_someone)
    val who = if (row.others > 0) {
        pluralStringResource(R.plurals.notifications_and_others, row.others, name, row.others)
    } else {
        name
    }
    return when (row.kind) {
        NotificationKind.Mention -> stringResource(R.string.notifications_mention, who)
        NotificationKind.Reblog -> stringResource(R.string.notifications_reblog, who)
        NotificationKind.Favourite -> stringResource(R.string.notifications_favourite, who)
        NotificationKind.Follow -> stringResource(R.string.notifications_follow, who)
        NotificationKind.FollowRequest -> stringResource(R.string.notifications_follow_request, who)
        NotificationKind.Poll -> stringResource(R.string.notifications_poll)
        NotificationKind.Status -> stringResource(R.string.notifications_status, who)
        NotificationKind.Update -> stringResource(R.string.notifications_update, who)
        NotificationKind.ModerationWarning -> stringResource(R.string.notifications_moderation)
        NotificationKind.SeveredRelationships -> stringResource(R.string.notifications_severed)
        NotificationKind.AdminSignUp -> stringResource(R.string.notifications_admin_sign_up, who)
        NotificationKind.AdminReport -> stringResource(R.string.notifications_admin_report, who)
        NotificationKind.AnnualReport -> stringResource(R.string.notifications_annual_report)
        NotificationKind.Unknown -> who
    }
}

@Composable
private fun Empty(filtered: Boolean) {
    Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
        Text(
            stringResource(if (filtered) R.string.notifications_empty_filtered else R.string.notifications_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private val NotificationKind.icon: ImageVector
    get() = when (this) {
        NotificationKind.Mention -> AlohaIcons.Reply
        NotificationKind.Reblog -> AlohaIcons.Boost
        NotificationKind.Favourite -> AlohaIcons.Favourited
        NotificationKind.Follow, NotificationKind.FollowRequest, NotificationKind.AdminSignUp -> AlohaIcons.AddAccount
        NotificationKind.Poll -> AlohaIcons.Poll
        NotificationKind.Update -> AlohaIcons.Edited
        NotificationKind.ModerationWarning, NotificationKind.SeveredRelationships -> AlohaIcons.ContentWarning
        NotificationKind.AdminReport -> AlohaIcons.Report
        else -> AlohaIcons.Notifications
    }

@Composable
private fun NotificationKind.tint() = when (this) {
    NotificationKind.ModerationWarning, NotificationKind.SeveredRelationships -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

private val Trouble.message: Int
    get() = when (this) {
        Trouble.Offline -> R.string.notifications_offline
        Trouble.RateLimited -> R.string.notifications_rate_limited
        Trouble.Server -> R.string.notifications_error
    }

/** How many lines of a post a notification row shows. */
internal const val PREVIEW_LINES = 2
private val KIND_ICON = 20.dp
private val AVATAR = 32.dp
