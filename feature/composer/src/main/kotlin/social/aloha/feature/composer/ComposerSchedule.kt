// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.fullDate

/** The earliest a server takes a scheduled post: five minutes ahead. */
internal val SCHEDULE_FLOOR: Duration = Duration.ofMinutes(5)

/** When the post goes out; a tap changes it, the cross posts it at once again. */
@Composable
internal fun ScheduleLine(at: Instant, onChange: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onChange,
            label = { Text(stringResource(R.string.composer_scheduled_for, fullDate(at))) },
            leadingIcon = { Icon(AlohaIcons.Schedule, contentDescription = null) },
        )
        IconButton(onClick = onClear) { Icon(AlohaIcons.Close, stringResource(R.string.composer_schedule_clear)) }
    }
}

/**
 * A day, then a time of day, in the phone's time zone; a time less than five minutes ahead cannot be
 * picked, as no server would take it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDialog(initial: Instant?, now: () -> Instant, onPick: (Instant) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = initial ?: now().plus(Duration.ofHours(1))
    var day by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now(zone)
    val chosen = day
    if (chosen == null) {
        val dates = rememberDatePickerState(
            initialSelectedDateMillis = start.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
                .toEpochMilli(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    // the picker's days are UTC midnights
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

                    override fun isSelectableYear(year: Int): Boolean = year >= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { day = dates.selectedDateMillis }, enabled = dates.selectedDateMillis != null) {
                    Text(stringResource(R.string.composer_schedule_next))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_cancel)) } },
        ) { DatePicker(dates) }
    } else {
        TimeDialog(LocalDate.ofEpochDay(chosen / MILLIS_PER_DAY), start.atZone(zone).toLocalTime(), now, onPick) {
            day = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    date: LocalDate,
    initial: LocalTime,
    now: () -> Instant,
    onPick: (Instant) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val time = rememberTimePickerState(
        initial.hour,
        initial.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    val at = date.atTime(time.hour, time.minute).atZone(zone).toInstant()
    val tooSoon = at.isBefore(now().plus(SCHEDULE_FLOOR))
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(stringResource(R.string.composer_schedule_time)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                TimePicker(time)
                if (tooSoon) {
                    Text(
                        stringResource(R.string.composer_schedule_too_soon),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(at) }, enabled = !tooSoon) {
                Text(stringResource(R.string.composer_schedule_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.composer_schedule_back)) } },
    )
}

/** A thread cannot be scheduled: nothing could answer a post that is not there yet. */
@Composable
internal fun ScheduleButton(state: ComposerUiState, actions: LaterActions) {
    if (state.remaining.size == 1 && state.posted == 0 && !state.editing) {
        IconButton(onClick = actions::onPickSchedule, enabled = !state.posting) {
            Icon(AlohaIcons.Schedule, stringResource(R.string.composer_schedule))
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
