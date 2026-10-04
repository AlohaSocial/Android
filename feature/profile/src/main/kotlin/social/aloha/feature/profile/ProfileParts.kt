// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.text.NumberFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import social.aloha.core.data.Trouble
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Story
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.ProvideLinkRouting
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.contentDirection
import social.aloha.core.ui.fullDate
import social.aloha.core.ui.readingWidth
import social.aloha.core.ui.rememberEmojiContent

@Composable
internal fun Header(header: ProfileHeader, state: ProfileUiState, actions: ProfileScreenActions) {
    Column(Modifier.semantics { isTraversalGroup = true }) {
        Box {
            AsyncImage(
                model = header.headerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(
                    BANNER_RATIO,
                ).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Avatar(
                header.author.avatarUrl,
                AVATAR,
                Modifier.align(Alignment.BottomStart).offset(x = AlohaSpacing.m, y = AVATAR / 2),
            )
        }
        // drawn beside the avatar, read after who the account is: a follow button before a name means nothing
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AlohaSpacing.m)
                .height(AVATAR / 2 + AlohaSpacing.xs)
                .semantics { traversalIndex = 1f },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (header.isSelf) {
                OutlinedButton(onClick = actions::onEditProfile) { Text(stringResource(R.string.profile_edit)) }
            } else {
                state.relation?.let { RelationButton(it, state.changing, actions) }
            }
        }
        Column(
            Modifier.padding(horizontal = AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Text(
                header.author.name,
                inlineContent = rememberEmojiContent(header.emojis, animate = true),
                style = MaterialTheme.typography.headlineSmall.contentDirection(),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                header.author.handle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Badges(header, state.relation)
            if (header.note.isNotEmpty()) {
                Text(
                    header.note,
                    style = MaterialTheme.typography.bodyLarge.contentDirection(),
                    inlineContent = rememberEmojiContent(header.emojis, animate = true),
                )
            }
            header.fields.forEach { Field(it) }
            Counts(header, actions)
        }
    }
}

@Composable
private fun RelationButton(relation: Relation, changing: Boolean, actions: ProfileScreenActions) {
    when {
        relation.blocking -> OutlinedButton(onClick = {
            actions.onChange(RelationshipChange.Unblock)
        }, enabled = !changing) {
            Text(stringResource(R.string.profile_unblock))
        }

        relation.blockedBy -> Unit

        relation.following || relation.requested -> OutlinedButton(
            onClick = { actions.onChange(RelationshipChange.Unfollow) },
            enabled = !changing,
        ) {
            Text(stringResource(if (relation.following) R.string.profile_following else R.string.profile_requested))
        }

        else -> Button(onClick = { actions.onChange(RelationshipChange.Follow()) }, enabled = !changing) {
            Text(stringResource(if (relation.followedBy) R.string.profile_follow_back else R.string.profile_follow))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(header: ProfileHeader, relation: Relation?) {
    val labels = buildList {
        if (relation?.followedBy == true) add(stringResource(R.string.profile_follows_you))
        if (header.locked) add(stringResource(R.string.profile_locked))
        if (header.author.bot) add(stringResource(R.string.profile_bot))
    }
    if (labels.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        labels.forEach { label ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = AlohaSpacing.xs, vertical = AlohaSpacing.xxs),
                )
            }
        }
    }
}

/** A field reads as its name and value; a verified link says so in words as well as with the tick. */
@Composable
private fun Field(field: ProfileHeader.Field) {
    val verified = stringResource(R.string.profile_field_verified)
    Column(Modifier.fillMaxWidth()) {
        Text(
            field.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        ) {
            if (field.verified) {
                Icon(
                    AlohaIcons.Voted,
                    contentDescription = verified,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(VERIFIED),
                )
            }
            Text(field.value, style = MaterialTheme.typography.bodyMedium.contentDirection())
        }
    }
}

/** Posts, following and followers; without the numbers, the two lists are still a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Counts(header: ProfileHeader, actions: ProfileScreenActions) {
    val format = remember { NumberFormat.getIntegerInstance() }
    val numbers = LocalReadingStyle.current.showCounts
    FlowRow(verticalArrangement = Arrangement.Center) {
        if (numbers) {
            Text(
                pluralStringResource(R.plurals.profile_posts, header.posts, format.format(header.posts)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = AlohaSpacing.s).align(Alignment.CenterVertically),
            )
        }
        TextButton(onClick = { actions.onPeople(followers = false) }) {
            Text(
                if (numbers) {
                    stringResource(R.string.profile_following_count, format.format(header.following))
                } else {
                    stringResource(R.string.profile_following_plain)
                },
            )
        }
        TextButton(onClick = { actions.onPeople(followers = true) }) {
            Text(
                if (numbers) {
                    pluralStringResource(R.plurals.profile_followers, header.followers, format.format(header.followers))
                } else {
                    stringResource(R.string.profile_followers_plain)
                },
            )
        }
    }
}

/** Twelve weeks as bars, read aloud as the one line they come to. */
@Composable
internal fun Highlights(highlights: ProfileHighlights) {
    val rhythm = stringResource(
        when (highlights.rhythm) {
            ProfileHighlights.Rhythm.Quiet -> R.string.profile_rhythm_quiet
            ProfileHighlights.Rhythm.Busier -> R.string.profile_rhythm_busier
            ProfileHighlights.Rhythm.Slowing -> R.string.profile_rhythm_slowing
            ProfileHighlights.Rhythm.Steady -> R.string.profile_rhythm_steady
        },
    )
    val total = pluralStringResource(R.plurals.profile_highlights_total, highlights.total, highlights.total)
    val heading = stringResource(R.string.profile_highlights)
    val spoken = stringResource(R.string.profile_highlights_spoken, heading, total, rhythm)
    val tallest = highlights.weeks.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(
        Modifier.fillMaxWidth().padding(AlohaSpacing.m).clearAndSetSemantics {
            contentDescription = spoken
        },
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().height(BARS),
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
            verticalAlignment = Alignment.Bottom,
        ) {
            highlights.weeks.forEach { week ->
                // an empty week keeps a sliver, so the twelve still read as twelve
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(maxOf(week.toFloat() / tallest, EMPTY_WEEK))
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                )
            }
        }
        Text(
            "$total · $rhythm",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CollectionRow(collection: MediaCollection, actions: ProfileScreenActions) {
    ListItem(
        modifier = Modifier.clickable { actions.onAlbum(collection) },
        leadingContent = { Thumbnail(collection.thumbnail) },
        headlineContent = { Text(collection.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.profile_collection_size, collection.postCount, collection.postCount))
        },
    )
}

@Composable
internal fun StoryRow(story: Story, actions: ProfileScreenActions) {
    ListItem(
        modifier = Modifier.clickable(enabled = story.url != null) { story.url?.let(actions::onOpenInBrowser) },
        leadingContent = { Thumbnail(story.previewUrl) },
        headlineContent = {
            Text(
                story.caption?.takeIf {
                    it.isNotBlank()
                } ?: stringResource(R.string.profile_tab_stories),
                maxLines = 2,
            )
        },
        supportingContent = { story.publishedAt?.let { Text(fullDate(it)) } },
    )
}

@Composable
private fun Thumbnail(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(
            THUMBNAIL,
        ).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small),
    )
}

private val AVATAR = 80.dp
private const val BANNER_RATIO = 3f
private val VERIFIED = 16.dp
private val BARS = 40.dp
private val BAR_GAP = 3.dp
private const val EMPTY_WEEK = 0.04f
private val THUMBNAIL = 56.dp
