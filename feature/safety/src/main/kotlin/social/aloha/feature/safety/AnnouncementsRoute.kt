// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Announcement
import social.aloha.core.model.Reaction
import social.aloha.core.navigation.AnnouncementsKey
import social.aloha.core.ui.ProvideLinkRouting
import social.aloha.core.ui.Reactions
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.contentDirection
import social.aloha.core.ui.fullDate
import social.aloha.core.ui.toAnnotatedString

/** What the reader's server announces; its links open as a post's do, through [onLink]. */
@Composable
public fun AnnouncementsRoute(
    key: AnnouncementsKey,
    onLink: (RichLinkTarget) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<AnnouncementsViewModel, AnnouncementsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProvideLinkRouting(onLink) {
        AnnouncementsScreen(state, viewModel::onReact, viewModel::onReactionFailureShown, onBack, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnnouncementsScreen(
    state: AnnouncementsUiState,
    onReact: (id: String, name: String, add: Boolean) -> Unit,
    onReactionFailureShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.announcements_title)
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.announcements_reaction_failed)
    LaunchedEffect(state.reactionFailed) {
        if (state.reactionFailed) {
            onReactionFailureShown()
            snackbars.showSnackbar(refused)
        }
    }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.safety_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.announcements.isEmpty() && state.loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.announcements.isEmpty() -> Text(
                    stringResource(if (state.failed) R.string.announcements_failed else R.string.announcements_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(AlohaSpacing.l),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.announcements, key = { it.id }) { announcement ->
                        AnnouncementItem(announcement, announcement.id in state.fresh) { name, add ->
                            onReact(announcement.id, name, add)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** When it was published, whether it is new, what it says, and the reactions to it. */
@Composable
private fun AnnouncementItem(announcement: Announcement, fresh: Boolean, onReact: (String, Boolean) -> Unit) {
    val colors = RichTextColors.fromTheme()
    val text = remember(announcement.content, colors) {
        StatusHtmlParser.parse(announcement.content).toAnnotatedString(colors)
    }
    Column(Modifier.fillMaxWidth().padding(AlohaSpacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            announcement.publishedAt?.let {
                Text(
                    fullDate(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (fresh) {
                Badge(Modifier.padding(start = AlohaSpacing.s)) { Text(stringResource(R.string.announcements_new)) }
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.contentDirection(),
            modifier = Modifier.padding(vertical = AlohaSpacing.s),
        )
        Reactions(announcement.reactions.map { Reaction(it.name, it.count, it.me, it.url) }, canReact = true) {
                name,
                add,
            ->
            onReact(name, add)
        }
    }
}

/**
 * On Home, while the server has announcements the reader has not read: how many, opening them. It
 * asks again each time Home comes back, so it goes once they are read.
 */
@Composable
public fun AnnouncementsBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<AnnouncementsBannerViewModel>()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }
    if (unread == 0) return
    OutlinedCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
    ) {
        ListItem(
            leadingContent = { Icon(AlohaIcons.News, contentDescription = null) },
            headlineContent = { Text(pluralStringResource(R.plurals.announcements_unread, unread, unread)) },
        )
    }
}
