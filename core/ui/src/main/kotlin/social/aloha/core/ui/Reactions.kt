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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Reaction

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

/** A reaction of the reader's own, from the ones most reached for; one they gave already is not offered. */
@Composable
private fun AddReaction(reactions: List<Reaction>, onReact: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val given = reactions.filter { it.me }.mapTo(HashSet()) { it.name }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Icon(AlohaIcons.AddReaction, stringResource(R.string.status_react)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // Nextcloud Social takes Unicode emoji only, so a short row of them rather than a picker
            Row {
                QUICK_REACTIONS.filterNot { it in given }.forEach { emoji ->
                    TextButton(onClick = {
                        open = false
                        onReact(emoji)
                    }) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
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
            AsyncImage(image, contentDescription = reaction.name, modifier = Modifier.size(REACTION_EMOJI))
        } else {
            Text(reaction.name)
        }
        Text(reaction.count.toString(), style = MaterialTheme.typography.labelLarge)
    }
}

private val REACTION_EMOJI = 18.dp
