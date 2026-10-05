// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import java.time.Duration
import java.time.Instant
import social.aloha.core.designsystem.AlohaSpacing

/** How long a pause can last, from half an hour to a week. */
internal val PAUSES: List<Duration> = listOf(
    Duration.ofMinutes(30),
    Duration.ofHours(1),
    Duration.ofHours(12),
    Duration.ofDays(1),
    Duration.ofDays(3),
    Duration.ofDays(7),
)

/** A pause's length in its largest whole unit, "30 minutes" or "3 days". */
internal fun pauseLength(length: Duration, locale: ULocale = ULocale.getDefault()): String {
    val measure = when {
        length.toMinutes() % MINUTES_PER_DAY == 0L -> Measure(length.toDays(), MeasureUnit.DAY)
        length.toMinutes() % MINUTES_PER_HOUR == 0L -> Measure(length.toHours(), MeasureUnit.HOUR)
        else -> Measure(length.toMinutes(), MeasureUnit.MINUTE)
    }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(measure)
}

private const val MINUTES_PER_DAY = 1_440L
private const val MINUTES_PER_HOUR = 60L

@Composable
internal fun PauseDialog(onPause: (Duration) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_pause)) },
        text = {
            // six lengths at a large font size are more than a dialog holds
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.notifications_pause_body), style = MaterialTheme.typography.bodyMedium)
                PAUSES.forEach { length ->
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Button) { onPause(length) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(pauseLength(length)) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notifications_pause_cancel)) }
        },
    )
}

/** Over the list while notifications wait: until when, and a way to have them back now. */
@Composable
internal fun PausedBanner(until: Instant, onResume: () -> Unit) {
    val context = LocalContext.current
    val millis = until.toEpochMilli()
    val day = if (DateUtils.isToday(millis)) 0 else DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
    val at = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME or day)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = AlohaSpacing.m, end = AlohaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.notifications_paused, at),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = AlohaSpacing.s),
            )
            TextButton(onClick = onResume) { Text(stringResource(R.string.notifications_resume)) }
        }
    }
}
