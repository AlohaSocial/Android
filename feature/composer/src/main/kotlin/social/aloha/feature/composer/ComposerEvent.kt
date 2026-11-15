// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import java.time.Duration
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.StatusEvent
import social.aloha.core.ui.fullDate

/** What keeps this event from going out: it needs a title and a start, and an end, where given, after the start. */
internal val StatusEvent.problem: EventProblem?
    get() {
        val start = startsAt
        return when {
            name.isBlank() -> EventProblem.NoTitle
            start == null -> EventProblem.NoStart
            endsAt?.isAfter(start) == false -> EventProblem.EndBeforeStart
            else -> null
        }
    }

/**
 * This state holding [event] as its server takes it: none where it takes no event, its status only where
 * it keeps one, and none where it would post a cancelled event as going ahead, which [eventLeftOut] says.
 */
internal fun ComposerUiState.withEvent(event: StatusEvent?): ComposerUiState {
    val cancelled = events && !eventStatuses && event?.status == StatusEvent.Status.Cancelled
    val kept = event.takeIf { events && !cancelled }
    return copy(
        event = if (eventStatuses) kept else kept?.copy(status = null),
        eventLeftOut = eventLeftOut || cancelled,
    )
}

/** An event, on a server that takes one and a new post without a poll; removing it is always allowed. */
@Composable
internal fun EventToggle(state: ComposerUiState, actions: EventActions) {
    if (!state.events || state.editing) return
    OutlinedIconToggleButton(
        checked = state.event != null,
        onCheckedChange = { actions.onEvent(if (it) StatusEvent() else null) },
        enabled = (state.poll == null || state.event != null) && !state.posting,
    ) {
        Icon(AlohaIcons.Event, stringResource(R.string.composer_event_add_toggle))
    }
}

private enum class EventTime { Start, End }

/** The event under the opening post: its title, when it starts and, where given, ends, and where it is. */
@Composable
internal fun EventEditor(event: StatusEvent, onEvent: (StatusEvent?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf<EventTime?>(null) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.composer_event),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { onEvent(null) }) {
                    Icon(AlohaIcons.Close, stringResource(R.string.composer_event_remove))
                }
            }
            EventField(event.name, R.string.composer_event_title) { onEvent(event.copy(name = it)) }
            TimeChip(
                event.startsAt,
                R.string.composer_event_starts,
                R.string.composer_event_pick_start,
                AlohaIcons.Event,
            ) {
                picking = EventTime.Start
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeChip(
                    event.endsAt,
                    R.string.composer_event_ends,
                    R.string.composer_event_pick_end,
                    AlohaIcons.Schedule,
                ) {
                    picking = EventTime.End
                }
                if (event.endsAt != null) {
                    IconButton(onClick = { onEvent(event.copy(endsAt = null)) }) {
                        Icon(AlohaIcons.Close, stringResource(R.string.composer_event_end_clear))
                    }
                }
            }
            EventField(event.location, R.string.composer_event_location) { onEvent(event.copy(location = it)) }
            event.problem?.let { problem ->
                Text(
                    stringResource(problem.message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
    picking?.let { time ->
        val start = event.startsAt
        ScheduleDialog(
            initial = if (time == EventTime.End) event.endsAt ?: start?.plus(Duration.ofHours(1)) else start,
            now = Instant::now,
            onPick = { at ->
                picking = null
                onEvent(if (time == EventTime.Start) event.copy(startsAt = at) else event.copy(endsAt = at))
            },
            onDismiss = { picking = null },
            floor = null,
            confirm = R.string.composer_event_time_confirm,
        )
    }
}

/** When the event starts or ends, [set] holding the time, or [unset] asking for one; a tap picks it. */
@Composable
private fun TimeChip(at: Instant?, @StringRes set: Int, @StringRes unset: Int, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(at?.let { stringResource(set, fullDate(it)) } ?: stringResource(unset)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

@Composable
private fun EventField(value: String, @StringRes label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
