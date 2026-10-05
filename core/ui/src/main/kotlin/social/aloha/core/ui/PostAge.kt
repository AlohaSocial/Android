// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.icu.text.DateFormat
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.text.format.DateFormat as DateFormat24
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.launch

/**
 * How old a post is. Anything under a minute old, and anything dated ahead of the phone, is "now": a
 * server clock a little ahead, or a round trip finishing after `created_at`, otherwise has the app claim
 * a post arrives from the future. Older posts count minutes, hours and days; after a week, the date.
 */
public sealed interface PostAge {
    public data object Now : PostAge

    public data class Ago(val amount: Long, val unit: Unit) : PostAge

    public data class On(val date: Instant) : PostAge

    public enum class Unit { Minutes, Hours, Days }

    public companion object {
        public fun of(createdAt: Instant, now: Instant): PostAge {
            val age = Duration.between(createdAt, now)
            return when {
                age < Duration.ofMinutes(1) -> Now
                age < Duration.ofHours(1) -> Ago(age.toMinutes(), Unit.Minutes)
                age < Duration.ofDays(1) -> Ago(age.toHours(), Unit.Hours)
                age < Duration.ofDays(WEEK_DAYS) -> Ago(age.toDays(), Unit.Days)
                else -> On(createdAt)
            }
        }

        private const val WEEK_DAYS = 7L
    }
}

/** The timeline's compact form, "5m" or "2d"; [now] is the word for [PostAge.Now]. */
public fun PostAge.short(now: String, locale: ULocale = ULocale.getDefault()): String = when (this) {
    PostAge.Now -> now

    is PostAge.Ago -> MeasureFormat.getInstance(
        locale,
        MeasureFormat.FormatWidth.NARROW,
    ).format(Measure(amount, unit.measure))

    is PostAge.On -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date.from(date))
}

/** The spoken form for screen readers, "5 minutes ago"; [now] is the word for [PostAge.Now]. */
public fun PostAge.spoken(now: String, locale: ULocale = ULocale.getDefault()): String = when (this) {
    PostAge.Now -> now

    is PostAge.Ago -> RelativeDateTimeFormatter.getInstance(locale)
        .format(amount.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit.relative)

    is PostAge.On -> DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date.from(date))
}

private val PostAge.Unit.measure: MeasureUnit get() = when (this) {
    PostAge.Unit.Minutes -> MeasureUnit.MINUTE
    PostAge.Unit.Hours -> MeasureUnit.HOUR
    PostAge.Unit.Days -> MeasureUnit.DAY
}

private val PostAge.Unit.relative: RelativeDateTimeFormatter.RelativeUnit get() = when (this) {
    PostAge.Unit.Minutes -> RelativeDateTimeFormatter.RelativeUnit.MINUTES
    PostAge.Unit.Hours -> RelativeDateTimeFormatter.RelativeUnit.HOURS
    PostAge.Unit.Days -> RelativeDateTimeFormatter.RelativeUnit.DAYS
}

/**
 * When a post was made, as a reader who chose times over ages reads it: the time for today's, "14:32"
 * (or "2:32 PM" without [hours24]), the day for this year's, "3 Oct", and the date before that.
 */
public fun absoluteTime(
    instant: Instant,
    now: Instant,
    hours24: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: ULocale = ULocale.getDefault(),
): String {
    val day = instant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val skeleton = when {
        day == today -> if (hours24) "Hm" else "hm"
        day.year == today.year -> "MMMd"
        else -> "yMMMd"
    }
    val format = DateFormat.getInstanceForSkeleton(skeleton, locale)
    format.timeZone = TimeZone.getTimeZone(zone.id)
    return format.format(Date.from(instant))
}

/**
 * A post's age, or its time where the reader chose times; a tap shows the full date and time above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PostTime(at: Instant, now: Instant, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val hours24 = DateFormat24.is24HourFormat(LocalContext.current)
    val text = if (LocalReadingStyle.current.absoluteTimes) {
        absoluteTime(at, now, hours24)
    } else {
        PostAge.of(at, now).short(stringResource(R.string.status_age_now))
    }
    TooltipBox(
        TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        { PlainTooltip { Text(fullDate(at)) } },
        tooltip,
        modifier,
        enableUserInput = false,
    ) {
        Text(
            text,
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.status_age_show_date)) {
                scope.launch { tooltip.show() }
            },
        )
    }
}

/** The date and time a post was made, in full, for the post a thread is about; to the second [withSeconds]. */
public fun fullDate(instant: Instant, locale: ULocale = ULocale.getDefault(), withSeconds: Boolean = false): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, if (withSeconds) DateFormat.MEDIUM else DateFormat.SHORT, locale)
        .format(Date.from(instant))
