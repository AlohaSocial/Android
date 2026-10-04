// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Notifications held back and raised as one summary at the [hours] of the day chosen, up to [MOST] of
 * them; the device stays quiet in between. With [personalNow], a private mention or one from someone the
 * person follows is still raised as it arrives.
 */
public data class Digest(val hours: List<Int>, val personalNow: Boolean = true) {
    /** The first digest time after [now]; one that falls into [quiet] hours waits until they end. */
    public fun nextAfter(now: ZonedDateTime, quiet: QuietHours?): ZonedDateTime = hours.ifEmpty { Default.hours }
        .map { hour -> if (quiet?.isQuiet(hour) == true) quiet.untilHour else hour }
        .distinct()
        .minOf { hour ->
            val today = now.truncatedTo(ChronoUnit.HOURS).withHour(hour)
            if (today.isAfter(now)) today else today.plusDays(1)
        }

    public companion object {
        public const val MOST: Int = 4
        public val Default: Digest = Digest(hours = listOf(8, 18))
    }
}
