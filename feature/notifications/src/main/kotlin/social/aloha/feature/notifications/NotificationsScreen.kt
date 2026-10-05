// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlinx.coroutines.launch
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.NotificationKind
import social.aloha.core.sync.NotificationText
import social.aloha.core.ui.CaughtUpDivider
import social.aloha.core.ui.EmptyState
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.NearEndEffect
import social.aloha.core.ui.PostAge
import social.aloha.core.ui.PostTime
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RefreshBox
import social.aloha.core.ui.Skeleton
import social.aloha.core.ui.TopBarTitle
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.itemMotion
import social.aloha.core.ui.readingColumn
import social.aloha.core.ui.rememberReducedMotion
import social.aloha.core.ui.rememberTopScroll
import social.aloha.core.ui.scrollToTop
import social.aloha.core.ui.spoken
import social.aloha.core.ui.topScrollTail

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
    val snackbars = remember { SnackbarHostState() }
    val notice = state.notice?.let { stringResource(it) }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbars.showSnackbar(notice)
            actions.onNoticeShown()
        }
    }
    val bar = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val scrollToTop = rememberTopScroll(listState)
    var pausing by remember { mutableStateOf(false) }
    if (pausing) {
        PauseDialog(onPause = {
            pausing = false
            actions.onPause(it)
        }, onDismiss = { pausing = false })
    }
    BoxWithConstraints(modifier) {
        val roomy = maxWidth >= ROOMY
        Scaffold(
            modifier = Modifier.nestedScroll(bar.nestedScrollConnection).semantics { paneTitle = title },
            snackbarHost = { SnackbarHost(snackbars) },
            topBar = {
                TopAppBar(
                    title = { TopBarTitle(title, scrollToTop) },
                    scrollBehavior = bar,
                    navigationIcon = navigationIcon,
                    actions = { BarActions(state, actions, roomy) { pausing = true } },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Chips(state.kinds, actions)
                state.pausedUntil?.takeIf { it.isAfter(state.now) }?.let { PausedBanner(it) { actions.onPause(null) } }
                PermissionBanner(state.askedForPermission, actions::onAskedForPermission)
                state.trouble?.let { TroubleStrip(stringResource(it.message)) }
                RefreshBox(
                    refreshing = state.refreshing,
                    onRefresh = actions::onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        state.rows.isNotEmpty() -> Rows(state, actions, listState)

                        state.loadedOnce && state.kinds.isNotEmpty() ->
                            EmptyState(stringResource(R.string.notifications_empty_filtered))

                        state.loadedOnce -> EmptyState(
                            stringResource(R.string.notifications_empty),
                            body = stringResource(R.string.notifications_empty_body),
                        )

                        else -> Skeleton(Modifier.fillMaxSize())
                    }
                }
            }
        }
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

/** The rows, with the caught-up line between the last one new since the previous visit and the first seen. */
@Composable
private fun Rows(state: NotificationsUiState, actions: NotificationsActions, listState: LazyListState) {
    val scope = rememberCoroutineScope()
    val tail = topScrollTail()
    val reduced = rememberReducedMotion()
    NearEndEffect(listState, state.rows.size, actions::onNearEnd)
    LazyColumn(
        state = listState,
        modifier = Modifier.readingColumn(),
        contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
    ) {
        if (state.pendingRequests > 0) item(key = REQUESTS) { RequestsRow(state.pendingRequests, actions::onRequests) }
        state.rows.forEachIndexed { index, row ->
            if (index > 0 && state.rows[index - 1].unread && !row.unread) {
                item(key = CAUGHT_UP) {
                    CaughtUpDivider(onClick = { scope.launch { listState.scrollToTop(tail, reduced) } })
                }
            }
            item(key = row.key) {
                Box(Modifier.itemMotion(this)) { NotificationRow(row, state.now, actions, state.origin) }
            }
        }
        if (state.loadingOlder) item { ListProgress() }
    }
}

private const val CAUGHT_UP = "caught-up"
private const val REQUESTS = "requests"

/**
 * One notification. A mention or reply can mute its conversation: a long press offers it, and a screen
 * reader finds it among the row's actions.
 */
@Composable
internal fun NotificationRow(
    row: NotificationRowUi,
    now: Instant,
    actions: NotificationsActions,
    origin: String? = null,
) {
    val summary = summary(row)
    val age = PostAge.of(row.at, now)
    val spokenAge = age.spoken(stringResource(UiR.string.status_age_now))
    val unread = stringResource(R.string.notifications_unread)
    val description = listOfNotNull(unread.takeIf { row.unread }, summary, row.preview, spokenAge).joinToString(". ")
    val background = if (row.unread) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val mutable = row.kind == NotificationKind.Mention && row.statusId != null
    val mute = stringResource(R.string.notifications_mute_conversation)
    val profiles = profileActions(row.people, actions::onProfile)
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(background)
                .combinedClickable(
                    role = Role.Button,
                    onLongClick = { if (mutable) menu = true },
                    onClick = { actions.onOpen(row) },
                )
                .semantics {
                    contentDescription = description
                    val muting = if (mutable) {
                        listOf(
                            CustomAccessibilityAction(mute) {
                                actions.onMuteConversation(row)
                                true
                            },
                        )
                    } else {
                        emptyList()
                    }
                    if (muting.isNotEmpty() || profiles.isNotEmpty()) customActions = muting + profiles
                }
                .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
        ) { RowContent(row, summary, now, actions, origin) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(mute) },
                leadingIcon = { Icon(AlohaIcons.MuteConversation, contentDescription = null) },
                onClick = {
                    menu = false
                    actions.onMuteConversation(row)
                },
            )
        }
    }
}

@Composable
private fun RowScope.RowContent(
    row: NotificationRowUi,
    summary: String,
    now: Instant,
    actions: NotificationsActions,
    origin: String?,
) {
    Icon(
        row.kind.icon,
        contentDescription = null,
        tint = row.kind.tint(),
        modifier = Modifier.padding(top = AlohaSpacing.xs).size(KIND_ICON),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Faces(row.people, actions::onProfile, Modifier.weight(1f))
            PostTime(row.at, now, MaterialTheme.typography.labelMedium, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            summaryWithName(summary, row.name, row.accountId, actions::onProfile),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (row.unread) FontWeight.SemiBold else null,
        )
        Details(row, actions, origin)
        if (row.groupKey != null && row.others > 0) {
            TextButton(onClick = { actions.onOthers(row.groupKey) }) {
                Text(
                    if (LocalReadingStyle.current.showCounts) {
                        pluralStringResource(R.plurals.notifications_others, row.others, row.others)
                    } else {
                        stringResource(R.string.notifications_others_plain)
                    },
                )
            }
        }
    }
}

/** What follows the summary: the post as a card or a preview, a notice, or a follow request's answers. */
@Composable
private fun Details(row: NotificationRowUi, actions: NotificationsActions, origin: String?) {
    when {
        row.kind in CARD_KINDS && (row.preview != null || row.media != null) -> CompactPost(row)

        row.severance != null || row.warning != null -> NoticeCard(row, origin, actions::onLearnMore)

        // a group answers in one go only where it names everyone in it
        row.kind == NotificationKind.FollowRequest && row.people.size == row.others + 1 ->
            RequestButtons { actions.onFollowRequest(row, it) }

        else -> row.preview?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun summary(row: NotificationRowUi): String = NotificationText.summary(
    LocalResources.current,
    row.kind,
    row.name,
    row.others,
    withCount = LocalReadingStyle.current.showCounts,
)

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
