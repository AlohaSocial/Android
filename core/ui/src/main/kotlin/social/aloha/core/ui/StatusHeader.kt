// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.LocalAlohaSemanticColors
import social.aloha.core.model.Visibility

/** Why the post is here, aligned with its content: who boosted it and when, with their face, or what it replies to. */
@Composable
internal fun ContextLineRow(context: StatusRowUi.ContextLine, now: Instant) {
    val icon = when (context) {
        is StatusRowUi.ContextLine.BoostedBy -> AlohaIcons.Boost
        StatusRowUi.ContextLine.Pinned -> AlohaIcons.Pinned
        StatusRowUi.ContextLine.ContinuedThread -> AlohaIcons.Thread
        is StatusRowUi.ContextLine.ReplyingTo, StatusRowUi.ContextLine.Replying -> AlohaIcons.Reply
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
        if (context is StatusRowUi.ContextLine.BoostedBy && context.avatarUrl != null) {
            Avatar(context.avatarUrl, SMALL_ICON)
        }
        Text(
            contextText(context, now),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Two lines: the name with its marks (bot, edited, visibility), then "age · @handle" in the outline colour.
 * The edited mark opens the post's edits.
 */
@Composable
internal fun StatusHeader(
    row: StatusRowUi,
    now: Instant,
    animateEmoji: Boolean,
    onHistory: () -> Unit,
    controls: CardControls,
    modifier: Modifier = Modifier,
) {
    val marks = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        ) {
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.author.bot) {
                    Icon(AlohaIcons.Bot, stringResource(R.string.status_bot), Modifier.size(SMALL_ICON), tint = marks)
                }
            }
            if (row.edited) Mark(AlohaIcons.Edited, stringResource(R.string.status_edited), onHistory)
            visibilityIcon(row.visibility)?.let { (icon, text) ->
                Icon(icon, stringResource(text), Modifier.size(SMALL_ICON), tint = marks)
            }
            controls.onRehide?.let { Mark(AlohaIcons.Sensitive, stringResource(R.string.status_rehide), it) }
            val collapse = controls.collapse
            if (collapse.collapsible && collapse.tall) {
                Mark(
                    if (collapse.expanded) AlohaIcons.ExpandLess else AlohaIcons.ExpandMore,
                    stringResource(if (collapse.expanded) R.string.status_collapse else R.string.status_expand),
                    collapse.onExpand,
                )
            }
        }
        Row {
            val meta = MaterialTheme.typography.bodySmall
            val outline = MaterialTheme.colorScheme.outline
            PostTime(row.createdAt, now, meta, outline)
            Text(" · ", style = meta, color = outline, maxLines = 1)
            // a handle reads left to right in a right-to-left interface too, its @ in front
            Text(
                row.author.handle,
                style = meta.copy(textDirection = TextDirection.Ltr),
                color = outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
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

/** A mark in the header the reader can tap: edited, hide again, expand or collapse. */
@Composable
private fun Mark(icon: ImageVector, label: String, onClick: () -> Unit) {
    Icon(
        icon,
        label,
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(MARK_PADDING).size(SMALL_ICON),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val MARK_PADDING = 4.dp
