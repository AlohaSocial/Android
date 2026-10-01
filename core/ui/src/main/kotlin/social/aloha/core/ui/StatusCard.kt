// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.LocalAlohaSemanticColors
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Visibility

/** What a row asks for; the screen that shows it decides what each does. */
@Immutable
public interface StatusActions {
    /** The thread of [statusId]: the post shown, or a quoted one. */
    public fun onOpen(statusId: String)

    public fun onProfile(accountId: String)

    public fun onLink(target: RichLinkTarget)

    public fun onMedia(row: StatusRowUi, index: Int)

    public fun onReply(row: StatusRowUi)

    public fun onBoost(row: StatusRowUi)

    public fun onFavourite(row: StatusRowUi)

    public fun onBookmark(row: StatusRowUi)

    public fun onVote(row: StatusRowUi, choices: List<Int>)

    public fun onReact(row: StatusRowUi, name: String, add: Boolean)

    public fun onMenu(row: StatusRowUi, item: StatusMenuItem)

    /** The menu items this screen can carry out; the others are not offered. */
    public val menu: Set<StatusMenuItem> get() = StatusMenuItem.entries.toSet()
}

/** The overflow menu. The author's own items appear only on their own posts. */
public enum class StatusMenuItem {
    Share,
    CopyLink,
    OpenInBrowser,
    Translate,
    MuteConversation,
    Report,
    Edit,
    Delete,
    Redraft,
    Pin,
    AddToAlbum,
    Archive,
    OpenInNewWindow,
}

/**
 * One post. The whole row is a single element for a screen reader: it reads who, when and what was
 * said, and offers the actions as custom actions rather than a stop per button. Every toggled action
 * draws a different glyph when on, so its state never rests on colour alone.
 *
 * @param now the time ages are counted from; the list passes one clock so every row agrees.
 * @param canReact whether the server takes emoji reactions (shown in a thread, where they are fetched).
 * @param focused the post a thread is about: larger text and the full date it was made.
 */
@Composable
public fun StatusCard(
    row: StatusRowUi,
    now: Instant,
    policy: SensitiveMediaPolicy,
    actions: StatusActions,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    canReact: Boolean = false,
    animateEmoji: Boolean = true,
    focused: Boolean = false,
) {
    var filterRevealed by rememberSaveable(row.rowId) { mutableStateOf(false) }
    val warning = row.filterWarning
    if (warning != null && !filterRevealed) {
        FilteredPlaceholder(warning, onReveal = { filterRevealed = true }, modifier = modifier)
        return
    }
    var spoilerRevealed by rememberSaveable(row.rowId) { mutableStateOf(false) }
    var pollChoice by rememberSaveable(row.rowId) { mutableStateOf(listOf<Int>()) }
    val bodyShown = row.spoiler == null || spoilerRevealed
    val label = accessibilityLabel(row, now, bodyShown)
    val controls =
        CardControls(spoilerRevealed, { spoilerRevealed = !spoilerRevealed }, pollChoice, { pollChoice = it })
    val customActions = customActions(row, actions, controls)
    ProvideLinkRouting(onLink = actions::onLink) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clickable { actions.onOpen(row.statusId) }
                .clearAndSetSemantics {
                    contentDescription = label
                    this.customActions = customActions
                    onClick { actions.onOpen(row.statusId).let { true } }
                }
                .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        ) {
            row.context?.let { ContextLineRow(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                Avatar(row.author.avatarUrl, AVATAR, Modifier.clickable { actions.onProfile(row.author.id) })
                StatusMain(
                    row,
                    now,
                    policy,
                    actions,
                    Flags(showActions, canReact, animateEmoji, focused),
                    controls,
                )
            }
        }
    }
}

/** How a card is drawn, beyond the row it draws. */
private data class Flags(
    val showActions: Boolean,
    val canReact: Boolean,
    val animateEmoji: Boolean,
    val focused: Boolean,
)

@Composable
private fun RowScope.StatusMain(
    row: StatusRowUi,
    now: Instant,
    policy: SensitiveMediaPolicy,
    actions: StatusActions,
    flags: Flags,
    controls: CardControls,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        StatusHeader(row, now, flags.animateEmoji)
        row.spoiler?.let { SpoilerToggle(it, row, controls.spoilerRevealed, flags.animateEmoji, controls.onSpoiler) }
        if (row.spoiler == null || controls.spoilerRevealed) {
            StatusBody(row, policy, actions, flags.canReact, flags.animateEmoji, flags.focused, controls)
        }
        if (flags.focused) {
            Text(
                fullDate(row.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (flags.showActions) ActionRow(row, actions)
    }
}

@Composable
public fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    }
}

@Composable
private fun ContextLineRow(context: StatusRowUi.ContextLine) {
    val (icon, text) = when (context) {
        is StatusRowUi.ContextLine.BoostedBy ->
            AlohaIcons.Boost to
                stringResource(R.string.status_context_boosted, context.name)

        StatusRowUi.ContextLine.Pinned -> AlohaIcons.Pinned to stringResource(R.string.status_context_pinned)

        StatusRowUi.ContextLine.ContinuedThread -> AlohaIcons.Thread to stringResource(R.string.status_context_thread)

        is StatusRowUi.ContextLine.ReplyingTo ->
            AlohaIcons.Reply to
                stringResource(R.string.status_context_replying_to, context.handle)

        StatusRowUi.ContextLine.Replying -> AlohaIcons.Reply to stringResource(R.string.status_context_replying)
    }
    val tint = when (context) {
        is StatusRowUi.ContextLine.BoostedBy -> LocalAlohaSemanticColors.current.boost
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.padding(start = AVATAR + AlohaSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(SMALL_ICON))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusHeader(row: StatusRowUi, now: Instant, animateEmoji: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
    ) {
        // who takes the room left over; when and the badges keep theirs
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        ) {
            Text(
                row.author.name,
                inlineContent = rememberEmojiContent(row.emojis, animateEmoji),
                style = MaterialTheme.typography.titleSmall.contentDirection(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // no weight: measured first, so the name keeps its room and the handle gives way
            )
            if (row.author.bot) {
                Icon(AlohaIcons.Bot, stringResource(R.string.status_bot), Modifier.size(SMALL_ICON), tint = muted)
            }
            Text(
                row.author.handle,
                style = MaterialTheme.typography.bodySmall.contentDirection(),
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Text(
            PostAge.of(row.createdAt, now).short(stringResource(R.string.status_age_now)),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            maxLines = 1,
        )
        if (row.edited) {
            Icon(
                AlohaIcons.Edited,
                stringResource(R.string.status_edited),
                Modifier.size(SMALL_ICON),
                tint = muted,
            )
        }
        visibilityIcon(row.visibility)?.let { (icon, text) ->
            Icon(icon, stringResource(text), Modifier.size(SMALL_ICON), tint = muted)
        }
    }
}

private fun visibilityIcon(visibility: Visibility): Pair<ImageVector, Int>? = when (visibility) {
    Visibility.Private -> AlohaIcons.VisibilityPrivate to R.string.status_visibility_private

    Visibility.Direct -> AlohaIcons.VisibilityDirect to R.string.status_visibility_direct

    Visibility.Unlisted -> AlohaIcons.VisibilityUnlisted to R.string.status_visibility_unlisted

    // an unknown visibility is treated as the most restrictive, so it is marked as such
    Visibility.Unknown -> AlohaIcons.VisibilityPrivate to R.string.status_visibility_private

    Visibility.Public -> null
}

/** A content warning collapses the body and the media, both. */
@Composable
private fun SpoilerToggle(
    spoiler: androidx.compose.ui.text.AnnotatedString,
    row: StatusRowUi,
    revealed: Boolean,
    animateEmoji: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(AlohaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Icon(AlohaIcons.ContentWarning, contentDescription = null, modifier = Modifier.size(SMALL_ICON))
            Text(
                spoiler,
                inlineContent = rememberEmojiContent(row.emojis, animateEmoji),
                style = MaterialTheme.typography.bodyMedium.contentDirection(),
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (revealed) R.string.status_spoiler_hide else R.string.status_spoiler_show),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                if (revealed) AlohaIcons.ExpandLess else AlohaIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(SMALL_ICON),
            )
        }
    }
}

@Composable
private fun FilteredPlaceholder(titles: List<String>, onReveal: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        Icon(AlohaIcons.Filtered, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.status_filtered, titles.joinToString()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReveal) { Text(stringResource(R.string.status_filtered_show)) }
    }
}

/** The four actions and the menu. Counts sit beside their button; the row itself reads as one element. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(row: StatusRowUi, actions: StatusActions) {
    // the menu keeps its place at the end; the actions spread across what is left and wrap where their
    // counts do not fit (a deep reply at a large font), so no count is ever cut short
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Actions(row, actions)
        }
        StatusMenu(row, actions)
    }
}

@Composable
private fun Actions(row: StatusRowUi, actions: StatusActions) {
    val semantic = LocalAlohaSemanticColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ActionButton(AlohaIcons.Reply, row.counts.replies, muted) { actions.onReply(row) }
    ActionButton(
        if (row.state.boosted) AlohaIcons.Boosted else AlohaIcons.Boost,
        row.counts.boosts,
        if (row.state.boosted) semantic.boost else muted,
    ) {
        actions.onBoost(row)
    }
    ActionButton(
        if (row.state.favourited) AlohaIcons.Favourited else AlohaIcons.Favourite,
        row.counts.favourites,
        if (row.state.favourited) semantic.favourite else muted,
    ) {
        actions.onFavourite(row)
    }
    ActionButton(
        if (row.state.bookmarked) AlohaIcons.Bookmarked else AlohaIcons.Bookmark,
        null,
        if (row.state.bookmarked) semantic.bookmark else muted,
    ) {
        actions.onBookmark(row)
    }
    // PeerTube's thumbs-down, read-only: the server carries the count but has no route to cast one
    if (row.counts.dislikes > 0 && row.media.any { it.type.isPlayable }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AlohaIcons.Dislike, contentDescription = null, tint = muted, modifier = Modifier.size(SMALL_ICON))
            Text(
                row.counts.dislikes.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(start = AlohaSpacing.xxs),
            )
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, count: Int?, tint: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = null, tint = tint) }
        Text(
            count?.takeIf { it > 0 }?.toString().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusMenu(row: StatusRowUi, actions: StatusActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.status_action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menuItems(row, actions.menu).forEach { (item, icon, text) ->
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.onMenu(row, item)
                    },
                )
            }
        }
    }
}

internal val AVATAR = 44.dp
internal val SMALL_ICON = 16.dp
