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
import social.aloha.core.ui.openInBrowser
import social.aloha.core.ui.readingColumn

@Composable
internal fun Trends(state: ModerationState, actions: ModerationActions) {
    LazyColumn(Modifier.readingColumn().fillMaxSize()) {
        item { Heading(stringResource(R.string.moderation_trends_tags)) }
        rows(state.tags, state.failed, R.string.moderation_trends_none) { tag ->
            Tag(tag) { approve -> actions.onReview(TrendKind.Tags, tag.id, approve) }
        }
        item { Heading(stringResource(R.string.moderation_trends_links)) }
        rows(state.links, state.failed, R.string.moderation_trends_none) { link ->
            Link(link) { approve -> actions.onReview(TrendKind.Links, link.id, approve) }
        }
        item { Heading(stringResource(R.string.moderation_trends_posts)) }
        rows(state.posts, state.failed, R.string.moderation_trends_none) { post ->
            Post(post) { approve -> actions.onReview(TrendKind.Statuses, post.id, approve) }
        }
    }
}

@Composable
private fun Tag(tag: AdminTag, onReview: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text("#${tag.name}") },
        supportingContent = { Text(pluralStringResource(R.plurals.moderation_trends_uses, tag.uses, tag.uses)) },
        trailingContent = { Review(onReview) },
    )
    HorizontalDivider()
}

@Composable
private fun Link(link: AdminLink, onReview: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(link.title.ifBlank { link.url }, maxLines = COMMENT_LINES) },
        supportingContent = {
            Text(link.providerName.ifBlank { link.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
    Row(Modifier.padding(horizontal = AlohaSpacing.m)) { Review(onReview) }
    HorizontalDivider()
}

@Composable
private fun Post(post: Status, onReview: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Avatar(post.account.avatar, AVATAR) },
        headlineContent = { Text(post.account.qualifiedHandle) },
        supportingContent = {
            Text(StatusHtmlParser.plainText(post.content), maxLines = COMMENT_LINES, overflow = TextOverflow.Ellipsis)
        },
    )
    Row(Modifier.padding(horizontal = AlohaSpacing.m)) { Review(onReview) }
    HorizontalDivider()
}

@Composable
private fun Review(onReview: (Boolean) -> Unit) {
    Row {
        TextButton(onClick = { onReview(true) }) { Text(stringResource(R.string.moderation_trends_allow)) }
        TextButton(onClick = { onReview(false) }) { Text(stringResource(R.string.moderation_trends_hide)) }
    }
}
