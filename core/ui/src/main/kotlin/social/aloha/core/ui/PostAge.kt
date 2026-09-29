// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.icu.text.DateFormat
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import java.time.Duration
import java.time.Instant
import java.util.Date

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
