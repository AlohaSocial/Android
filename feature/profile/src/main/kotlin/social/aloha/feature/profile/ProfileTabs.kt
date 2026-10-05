// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard

/** Posts, Posts & replies and Media: one Timeline tab, told apart by chips under the tabs. */
internal val TIMELINE = setOf(ProfileTab.Posts, ProfileTab.Replies, ProfileTab.Media)

/** The tabs as drawn: the three timelines folded into the first of them. */
internal fun shownTabs(tabs: List<ProfileTab>): List<ProfileTab> = tabs.filter { it !in TIMELINE - ProfileTab.Posts }

/** The tab drawn as chosen for [tab]: any of the timelines is the Timeline tab. */
internal fun shownTab(tab: ProfileTab): ProfileTab = if (tab in TIMELINE) ProfileTab.Posts else tab

/**
 * The tab row, and under it the Timeline's chips while a timeline shows. [pinned] takes the top bar's
 * tint and lift, as the row does once it sticks under the bar. [timeline] is the timeline the Timeline
 * tab goes back to.
 */
@Composable
internal fun Tabs(state: ProfileUiState, pinned: Boolean, timeline: ProfileTab, onTab: (ProfileTab) -> Unit) {
    val shown = shownTabs(state.tabs)
    val color = if (pinned) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    Surface(color = color, tonalElevation = if (pinned) LIFT else 0.dp) {
        Column {
            PrimaryScrollableTabRow(
                selectedTabIndex = shown.indexOf(shownTab(state.tab)).coerceAtLeast(0),
                edgePadding = AlohaSpacing.m,
                containerColor = color,
            ) {
                shown.forEach { tab ->
                    Tab(
                        selected = tab == shownTab(state.tab),
                        onClick = { onTab(if (tab == ProfileTab.Posts) timeline else tab) },
                        text = {
                            Text(
                                stringResource(
                                    if (tab ==
                                        ProfileTab.Posts
                                    ) {
                                        R.string.profile_tab_timeline
                                    } else {
                                        tab.label
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            if (state.tab in TIMELINE) TimelineChips(state.tab, onTab)
        }
    }
}

@Composable
private fun TimelineChips(tab: ProfileTab, onTab: (ProfileTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        listOf(ProfileTab.Posts, ProfileTab.Replies, ProfileTab.Media).forEach { option ->
            FilterChip(
                selected = option == tab,
                onClick = { onTab(option) },
                label = { Text(stringResource(option.label), maxLines = 1) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/** What the account features: its pinned posts and its hashtags, a few of each until "View all". */
internal fun LazyListScope.featured(featured: FeaturedUi, now: Instant, rowActions: StatusActions) {
    if (featured.posts.isNotEmpty()) {
        item(key = "pinned-title") { SectionTitle(R.string.profile_featured_pinned) }
        item(key = "pinned") {
            ViewAll(featured.posts, SHOWN) { posts ->
                posts.forEach { row ->
                    StatusCard(row, now, LocalSensitiveMediaPolicy.current, rowActions)
                    PostDivider()
                }
            }
        }
    }
    if (featured.tags.isNotEmpty()) {
        item(key = "tags-title") { SectionTitle(R.string.profile_featured_tags) }
        item(key = "tags") {
            ViewAll(featured.tags, SHOWN) { tags ->
                tags.forEach { tag ->
                    ListItem(
                        modifier = Modifier.clickable { rowActions.onLink(RichLinkTarget.Hashtag(tag.name)) },
                        headlineContent = { Text("#${tag.name}") },
                        supportingContent = if (LocalReadingStyle.current.showCounts) {
                            {
                                Text(
                                    pluralStringResource(
                                        R.plurals.profile_featured_tag_posts,
                                        tag.statusesCount,
                                        tag.statusesCount,
                                    ),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s).semantics { heading() },
    )
}

/** The first [shown] of [all], and the rest after "View all". */
@Composable
private fun <T> ViewAll(all: List<T>, shown: Int, content: @Composable (List<T>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        content(if (open) all else all.take(shown))
        if (!open && all.size > shown) {
            TextButton(onClick = { open = true }, modifier = Modifier.padding(horizontal = AlohaSpacing.xs)) {
                Text(stringResource(R.string.profile_featured_view_all))
            }
        }
    }
}

internal val ProfileTab.label: Int
    get() = when (this) {
        ProfileTab.Posts -> R.string.profile_tab_posts
        ProfileTab.Replies -> R.string.profile_tab_replies
        ProfileTab.Media -> R.string.profile_tab_media
        ProfileTab.Featured -> R.string.profile_tab_featured
        ProfileTab.Videos -> R.string.profile_tab_videos
        ProfileTab.Collections -> R.string.profile_tab_collections
        ProfileTab.Stories -> R.string.profile_tab_stories
    }

private const val SHOWN = 3
private val LIFT = 3.dp
