// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Reaction

/** The server's own custom emoji a reaction can be, offered after the Unicode ones; none by default. */
public val LocalReactionEmoji: ProvidableCompositionLocal<List<CustomEmoji>> = staticCompositionLocalOf { emptyList() }

/**
 * Emoji reactions, each with its count; one the reader chose shows a tick as well as its colour. With
 * [canReact], each is a chip that adds the reader's or takes it back, and one more offers another;
 * without, they are only shown.
 */
@Composable
public fun Reactions(
    reactions: List<Reaction>,
    canReact: Boolean,
    modifier: Modifier = Modifier,
    onReact: (name: String, add: Boolean) -> Unit,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        reactions.forEach { reaction ->
            if (canReact) {
                FilterChip(
                    selected = reaction.me,
                    onClick = { onReact(reaction.name, !reaction.me) },
                    label = { ReactionLabel(reaction) },
                    leadingIcon = if (reaction.me) {
                        {
                            Icon(
                                AlohaIcons.Favourited,
                                contentDescription = null,
                                modifier = Modifier.size(REACTION_EMOJI),
                            )
                        }
                    } else {
                        null
                    },
                )
            } else {
                // shown, not offered: a label rather than a chip that looks switched off
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
                    color = if (reaction.me) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ) {
                    Box(Modifier.padding(horizontal = AlohaSpacing.s, vertical = AlohaSpacing.xxs)) {
                        ReactionLabel(reaction)
                    }
                }
            }
        }
        if (canReact) AddReaction(reactions) { name -> onReact(name, true) }
    }
}

/**
 * A reaction of the reader's own, from the ones most reached for and the server's own custom emoji; one they
 * gave already is not offered.
 */
@Composable
private fun AddReaction(reactions: List<Reaction>, onReact: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val given = reactions.filter { it.me }.mapTo(HashSet()) { it.name }
    val custom = LocalReactionEmoji.current.filterNot { ":${it.shortcode}:" in given }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Icon(AlohaIcons.AddReaction, stringResource(R.string.status_react)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row {
                QUICK_REACTIONS.filterNot { it in given }.forEach { emoji ->
                    TextButton(onClick = {
                        open = false
                        onReact(emoji)
                    }) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                }
            }
            if (custom.isNotEmpty()) {
                FlowRow(Modifier.widthIn(max = CUSTOM_ROW)) {
                    custom.forEach { emoji ->
                        IconButton(onClick = {
                            open = false
                            onReact(":${emoji.shortcode}:")
                        }) {
                            EmojiImage(emoji.stillUrl, ":${emoji.shortcode}:", emoji.shortcode, CUSTOM_EMOJI)
                        }
                    }
                }
            }
        }
    }
}

private val QUICK_REACTIONS =
    listOf("\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83C\uDF89")

@Composable
private fun ReactionLabel(reaction: Reaction) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
    ) {
        val image = reaction.url ?: reaction.staticUrl
        if (image != null) {
            EmojiImage(image, reaction.name, reaction.name, REACTION_EMOJI)
        } else {
            Text(reaction.name)
        }
        if (LocalReadingStyle.current.showCounts) {
            Text(reaction.count.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A custom emoji's picture, or its [name] as text where the picture cannot be had. */
@Composable
private fun EmojiImage(url: String?, name: String, description: String, size: Dp) {
    var failed by remember(url) { mutableStateOf(url == null) }
    if (failed) {
        Text(name, Modifier.semantics { contentDescription = description })
    } else {
        AsyncImage(url, description, Modifier.size(size), onError = { failed = true })
    }
}

private val REACTION_EMOJI = 18.dp
private val CUSTOM_EMOJI = 28.dp
private val CUSTOM_ROW = 288.dp
