// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.Reordering
import social.aloha.core.ui.dragHandle
import social.aloha.core.ui.motion
import social.aloha.core.ui.moves
import social.aloha.core.ui.rememberReordering
import social.aloha.core.ui.reorderItem

/**
 * The poll under the opening post: its choices, how long it runs, and how it counts. A choice's handle drags
 * it to another place, or onto the "+" under them, which turns red, to take it out.
 */
@Composable
internal fun PollEditor(state: ComposerUiState, poll: PollUi, onPoll: (PollUi?) -> Unit) {
    val keys = remember { ChoiceKeys() }
    val ids = keys.of(poll.options.size)
    val removable = poll.options.size > 2
    val rows = remember { HashMap<Long, Rect>() }
    var plus by remember { mutableStateOf<Rect?>(null) }
    lateinit var reordering: Reordering<Long>
    val over = { removable && dropped(reordering, rows, plus) }
    reordering = rememberReordering(ids, key = { it }, gap = AlohaSpacing.xs) { order ->
        val dragged = reordering.dragged
        if (over()) {
            val index = ids.indexOf(dragged)
            keys.removed(index)
            onPoll(poll.copy(options = poll.options.filterIndexed { i, _ -> i != index }))
        } else {
            val options = order.map { poll.options[ids.indexOf(it)] }
            keys.ordered(order)
            onPoll(poll.copy(options = options))
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.composer_poll),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { onPoll(null) }) {
                    Icon(AlohaIcons.Close, stringResource(R.string.composer_poll_remove))
                }
            }
            reordering.order.forEachIndexed { place, id ->
                key(id) {
                    val index = ids.indexOf(id)
                    PollOption(
                        index,
                        poll,
                        state,
                        onPoll,
                        Modifier.onGloballyPositioned { rows[id] = it.boundsInRoot() }
                            .reorderItem(reordering, id),
                        handle = Modifier.dragHandle(reordering, id),
                        moves = reordering.moves(place),
                        removed = { keys.removed(index) },
                    )
                }
            }
            val dragging = reordering.dragged != null && removable
            if (dragging || poll.options.size < state.maxPollOptions) {
                AddChoice(
                    dragging,
                    over = dragging && over(),
                    Modifier.onGloballyPositioned { plus = it.boundsInRoot() },
                ) {
                    keys.added()
                    onPoll(poll.copy(options = poll.options + ""))
                }
            }
            DurationMenu(poll, state.pollDurations, onPoll)
            PollSwitch(stringResource(R.string.composer_poll_multiple), poll.multiple) {
                onPoll(poll.copy(multiple = it))
            }
            PollSwitch(stringResource(R.string.composer_poll_hide_totals), poll.hideTotals) {
                onPoll(poll.copy(hideTotals = it))
            }
        }
    }
}

/**
 * The "+" that adds a choice, or while one is dragged and can be spared, the place to drop it to take it out:
 * in the error colours, filled once the choice is over it.
 */
@Composable
private fun AddChoice(dragging: Boolean, over: Boolean, modifier: Modifier, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (over) colors.error else Color.Transparent, motion(tween()), label = "drop")
    val content by animateColorAsState(
        when {
            over -> colors.onError
            dragging -> colors.error
            else -> colors.primary
        },
        motion(tween()),
        label = "drop-content",
    )
    TextButton(
        onClick = onAdd,
        enabled = !dragging,
        colors = ButtonDefaults.textButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        modifier = modifier,
    ) {
        Icon(if (dragging) AlohaIcons.Delete else AlohaIcons.Add, contentDescription = null)
        Text(
            stringResource(if (dragging) R.string.composer_poll_drop_remove else R.string.composer_poll_add),
            Modifier.padding(start = AlohaSpacing.xs),
        )
    }
}

/** Whether the choice being dragged is over the "+", by its middle. */
private fun dropped(reordering: Reordering<Long>, rows: Map<Long, Rect>, plus: Rect?): Boolean {
    val row = reordering.dragged?.let { rows[it] } ?: return false
    val middle = row.center.y + reordering.offset
    return plus != null && middle in plus.top..plus.bottom
}

/** Keys for the poll's choices that stay with each choice as it moves, which its text cannot be. */
private class ChoiceKeys {
    private var keys = emptyList<Long>()
    private var next = 0L

    fun of(size: Int): List<Long> {
        if (keys.size != size) keys = List(size) { next++ }
        return keys
    }

    fun added() {
        keys = keys + next++
    }

    fun removed(index: Int) {
        keys = keys.filterIndexed { i, _ -> i != index }
    }

    fun ordered(order: List<Long>) {
        keys = order
    }
}

@Composable
private fun PollOption(
    index: Int,
    poll: PollUi,
    state: ComposerUiState,
    onPoll: (PollUi?) -> Unit,
    modifier: Modifier,
    handle: Modifier,
    moves: List<CustomAccessibilityAction>,
    removed: () -> Unit,
) {
    val option = poll.options[index]
    val over = option.trim().length > state.maxPollOptionCharacters
    OutlinedTextField(
        value = option,
        onValueChange = { text -> onPoll(poll.copy(options = poll.options.toMutableList().also { it[index] = text })) },
        label = { Text(stringResource(R.string.composer_poll_option, index + 1)) },
        // the field's Move up and Move down say what the handle does
        leadingIcon = { Icon(AlohaIcons.Reorder, contentDescription = null, handle.minimumInteractiveComponentSize()) },
        singleLine = true,
        isError = over,
        supportingText = if (over) {
            {
                Text(
                    pluralStringResource(
                        R.plurals.composer_poll_option_long,
                        state.maxPollOptionCharacters,
                        state.maxPollOptionCharacters,
                    ),
                )
            }
        } else {
            null
        },
        trailingIcon = if (poll.options.size > 2) {
            {
                IconButton(onClick = {
                    removed()
                    onPoll(poll.copy(options = poll.options.filterIndexed { i, _ -> i != index }))
                }) {
                    Icon(AlohaIcons.Remove, stringResource(R.string.composer_poll_option_remove, index + 1))
                }
            }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth().semantics { customActions = moves },
    )
}

@Composable
private fun DurationMenu(poll: PollUi, durations: List<Long>, onPoll: (PollUi?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.composer_poll_duration, durationLabel(poll.seconds)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            durations.forEach { seconds ->
                DropdownMenuItem(
                    text = { Text(durationLabel(seconds)) },
                    onClick = {
                        open = false
                        onPoll(poll.copy(seconds = seconds))
                    },
                )
            }
        }
    }
}

@Composable
private fun PollSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = AlohaSpacing.s))
    }
}

/** A poll's length in the largest whole unit: minutes, hours or days. */
@Composable
private fun durationLabel(seconds: Long): String {
    val minutes = (seconds / MINUTE).toInt()
    val hours = (seconds / HOUR).toInt()
    val days = (seconds / DAY).toInt()
    return when {
        seconds % DAY == 0L -> pluralStringResource(R.plurals.composer_poll_days, days, days)
        seconds % HOUR == 0L -> pluralStringResource(R.plurals.composer_poll_hours, hours, hours)
        else -> pluralStringResource(R.plurals.composer_poll_minutes, minutes, minutes)
    }
}

/** A poll, on an opening post without media or a card; removing it is always allowed. */
@Composable
internal fun PollToggle(state: ComposerUiState, actions: MediaActions) {
    val pollable = state.attachments.first().isEmpty() && !state.card.on
    OutlinedIconToggleButton(
        checked = state.poll != null,
        onCheckedChange = { actions.onPoll(if (it) PollUi(seconds = state.pollDurations.defaultLength()) else null) },
        enabled = (pollable || state.poll != null) && !state.posting,
    ) {
        Icon(AlohaIcons.Poll, stringResource(R.string.composer_poll_add_toggle))
    }
}

/** A day where the server allows it, else the nearest length it does. */
private fun List<Long>.defaultLength(): Long =
    minByOrNull { kotlin.math.abs(it - PollUi.DAY_SECONDS) } ?: PollUi.DAY_SECONDS

private const val MINUTE = 60L
private const val HOUR = 3_600L
private const val DAY = 86_400L

/** The other shapes the post may go out in, where it fits them: a card, or a story. */
@Composable
internal fun ShareAs(state: ComposerUiState, actions: ComposerActions) {
    if (state.cardFits) {
        OutlinedIconToggleButton(checked = state.card.on, onCheckedChange = actions::onCard) {
            Icon(AlohaIcons.TextCard, stringResource(R.string.composer_card))
        }
    }
    if (state.storyFits) {
        OutlinedIconToggleButton(checked = state.asStory, onCheckedChange = actions::onStory) {
            Icon(AlohaIcons.Story, stringResource(R.string.composer_story))
        }
    }
}

/** How long a story's picture or card shows for, from the lengths a story takes. */
@Composable
internal fun StoryLength(seconds: Int, onSeconds: (Int) -> Unit) {
    // at a large font the lengths scroll rather than wrap
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = AlohaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.composer_story_length), style = MaterialTheme.typography.labelLarge)
        StoryShare.LENGTHS.forEach { length ->
            FilterChip(
                selected = length == seconds,
                onClick = { onSeconds(length) },
                label = { Text(pluralStringResource(R.plurals.composer_story_seconds, length, length), maxLines = 1) },
            )
        }
    }
}

/** What only the opening post shows: the card it goes out as, and how long it shows as a story. */
@Composable
internal fun OpeningExtras(state: ComposerUiState, actions: ComposerActions) {
    if (state.card.on && state.cardFits) CardPreview(state.card, actions)
    if (state.storyLengthPicked) StoryLength(state.storySeconds, actions::onStorySeconds)
}
