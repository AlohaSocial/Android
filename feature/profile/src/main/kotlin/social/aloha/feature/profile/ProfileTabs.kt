// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
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
import social.aloha.core.model.FeaturedTag
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard

/** Posts and Posts & replies: one Posts tab, told apart by chips under the tabs. */
internal val TIMELINE = setOf(ProfileTab.Posts, ProfileTab.Replies)

/** The tabs as drawn: the two timelines folded into the first of them. */
internal fun shownTabs(tabs: List<ProfileTab>): List<ProfileTab> = tabs.filter { it !in TIMELINE - ProfileTab.Posts }

/** The tab drawn as chosen for [tab]: either timeline is the Posts tab. */
internal fun shownTab(tab: ProfileTab): ProfileTab = if (tab in TIMELINE) ProfileTab.Posts else tab

/**
 * The tab row, and under it the Posts tab's chips while a timeline shows. [pinned] takes the top bar's
 * tint and lift, as the row does once it sticks under the bar. [timeline] is the timeline the Posts tab
 * goes back to.
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
                        text = { Text(stringResource(tab.label)) },
                    )
                }
            }
            if (state.tab in TIMELINE) TimelineChips(state.tab, state.tabs.filter { it in TIMELINE }, onTab)
        }
    }
}

@Composable
private fun TimelineChips(tab: ProfileTab, options: List<ProfileTab>, onTab: (ProfileTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == tab,
                onClick = { onTab(option) },
                label = { Text(stringResource(option.label), maxLines = 1) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/** The posts the account pinned, above its Posts tab's timeline as on the web: a few until "View all". */
internal fun LazyListScope.pinnedPosts(featured: FeaturedUi, now: Instant, rowActions: StatusActions) {
    if (featured.posts.isEmpty()) return
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

/**
 * The hashtags the account features, in its header as on the web, each opening the account's posts with
 * it ([onTag]); with their counts where numbers show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeaturedTags(tags: List<FeaturedTag>, onTag: (String) -> Unit) {
    if (tags.isEmpty()) return
    val counts = LocalReadingStyle.current.showCounts
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        Text(
            stringResource(R.string.profile_featured_tags),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
            tags.forEach { tag ->
                AssistChip(
                    onClick = { onTag(tag.name) },
                    label = {
                        val uses = pluralStringResource(
                            R.plurals.profile_featured_tag_posts,
                            tag.statusesCount,
                            tag.statusesCount,
                        )
                        Text(if (counts) "#${tag.name} · $uses" else "#${tag.name}", maxLines = 1)
                    },
                )
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
        ProfileTab.Photos -> R.string.profile_tab_photos
        ProfileTab.Videos -> R.string.profile_tab_videos
        ProfileTab.Tagged -> R.string.profile_tab_tagged
        ProfileTab.Collections -> R.string.profile_tab_collections
        ProfileTab.Saved -> R.string.profile_tab_saved
        ProfileTab.Drafts -> R.string.profile_tab_drafts
        ProfileTab.Statistics -> R.string.profile_tab_statistics
    }

private const val SHOWN = 3
private val LIFT = 3.dp
