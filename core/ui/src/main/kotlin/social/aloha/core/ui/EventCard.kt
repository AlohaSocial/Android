// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.StatusEvent

/** An event post's event: its name, when and where, and a way into the reader's calendar once it has a start. */
@Composable
internal fun EventCard(event: StatusEvent, url: String?) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
        color = Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            if (event.name.isNotEmpty()) Text(event.name, style = MaterialTheme.typography.titleSmall)
            event.startsAt?.let { start ->
                // a new time zone does not recreate the activity
                val zone = ZoneId.systemDefault()
                val time = remember(start, event.endsAt, zone) { eventTime(start, event.endsAt, zone) }
                EventLine(AlohaIcons.Event, time)
            }
            if (event.location.isNotEmpty()) EventLine(AlohaIcons.Place, event.location)
            if (event.startsAt != null) {
                TextButton(onClick = { addToCalendar(context, event, url) }) {
                    Text(stringResource(R.string.status_event_add_to_calendar))
                }
            }
        }
    }
}

@Composable
private fun EventLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(EVENT_ICON))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The event as a screen reader says it, the row being one stop: its name, when it runs and where. */
internal fun spokenEvent(event: StatusEvent): String = listOfNotNull(
    event.name.ifEmpty { null },
    event.startsAt?.let { eventTime(it, event.endsAt) },
    event.location.ifEmpty { null },
).joinToString(", ")

/**
 * When an event runs, with its weekday and in the reader's zone: "Mon, Nov 2, 2026, 6:00 – 9:00 PM", the
 * day said once where it ends on the day it starts.
 */
internal fun eventTime(
    start: Instant,
    end: Instant?,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: ULocale = ULocale.getDefault(),
): String {
    val timeZone = TimeZone.getTimeZone(zone.id)
    if (end == null) {
        return DateFormat.getInstanceForSkeleton(SKELETON, locale)
            .apply { this.timeZone = timeZone }
            .format(Date.from(start))
    }
    return DateIntervalFormat.getInstance(SKELETON, locale)
        .apply { this.timeZone = timeZone }
        .format(DateInterval(start.toEpochMilli(), end.toEpochMilli()))
}

/** The device's calendar, with the event filled in for the reader to save; nothing is written without them. */
internal fun addToCalendar(context: Context, event: StatusEvent, url: String?) {
    val start = event.startsAt ?: return
    val insert = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
        .putExtra(CalendarContract.Events.TITLE, event.name)
    event.endsAt?.let { insert.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.toEpochMilli()) }
    if (event.location.isNotEmpty()) insert.putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
    url?.let { insert.putExtra(CalendarContract.Events.DESCRIPTION, it) }
    try {
        context.startActivity(insert)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.status_event_no_calendar, Toast.LENGTH_SHORT).show()
    }
}

private const val SKELETON = "yMMMEdjm"
private val EVENT_ICON = 18.dp
