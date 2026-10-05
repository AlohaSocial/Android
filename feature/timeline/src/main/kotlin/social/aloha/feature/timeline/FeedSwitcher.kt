// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.PinnedFeed

/**
 * The bar's title: the feed shown, by its icon and name, and a tap that lists every pinned feed, a dot
 * on those with news; or, where the reader chose it, a tap for the next feed and a long press to list.
 */
@Composable
internal fun FeedSwitcher(
    current: PinnedFeed,
    state: HomeFeedsState,
    onPick: (Int) -> Unit,
    onListed: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    val feeds = state.feeds
    val list = {
        onListed()
        open = true
    }
    val next = { onPick((feeds.indexOf(current) + 1) % feeds.size) }
    val news = stringResource(R.string.timeline_feed_new)
    val elsewhere = state.unread.any { it != current.id }
    Box {
        Row(
            Modifier
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(
                        if (state.titleNext) R.string.timeline_feeds_next else R.string.timeline_feeds_choose,
                    ),
                    onClick = if (state.titleNext) next else list,
                    onLongClickLabel = stringResource(R.string.timeline_feeds_choose).takeIf { state.titleNext },
                    onLongClick = list.takeIf { state.titleNext },
                )
                .semantics { if (elsewhere) stateDescription = news }
                .padding(horizontal = AlohaSpacing.xs, vertical = AlohaSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Icon(feedIcon(current), contentDescription = null)
            Text(
                feedName(current),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, false),
            )
            Icon(AlohaIcons.ExpandMore, contentDescription = null)
            if (elsewhere) Badge()
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            feeds.forEachIndexed { index, feed ->
                FeedItem(feed, fresh = feed.id in state.unread, shown = feed.id == current.id) {
                    open = false
                    onPick(index)
                }
            }
            onEdit?.let { edit ->
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.timeline_feeds_edit)) },
                    leadingIcon = { Icon(AlohaIcons.Edited, contentDescription = null) },
                    onClick = {
                        open = false
                        edit()
                    },
                )
            }
        }
    }
}

/** A feed in the list: the one shown checked and said selected, one with news marked and said so. */
@Composable
private fun FeedItem(feed: PinnedFeed, fresh: Boolean, shown: Boolean, onClick: () -> Unit) {
    val news = stringResource(R.string.timeline_feed_new)
    DropdownMenuItem(
        text = { Text(feedName(feed)) },
        leadingIcon = { Icon(feedIcon(feed), contentDescription = null) },
        trailingIcon = if (fresh || shown) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fresh) Badge()
                    if (shown) Icon(AlohaIcons.Check, contentDescription = null)
                }
            }
        } else {
            null
        },
        modifier = Modifier.semantics {
            selected = shown
            if (fresh) stateDescription = news
        },
        onClick = onClick,
    )
}

/** The ⋮ of a home timeline: whether its live feeds show boosts and replies, for the account in use. */
@Composable
internal fun ShowOptions(
    showBoosts: Boolean,
    showReplies: Boolean,
    onShowBoosts: (Boolean) -> Unit,
    onShowReplies: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.timeline_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_show_boosts)) },
                leadingIcon = { Checkbox(showBoosts, onCheckedChange = null) },
                onClick = { onShowBoosts(!showBoosts) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_show_replies)) },
                leadingIcon = { Checkbox(showReplies, onCheckedChange = null) },
                onClick = { onShowReplies(!showReplies) },
            )
        }
    }
}
