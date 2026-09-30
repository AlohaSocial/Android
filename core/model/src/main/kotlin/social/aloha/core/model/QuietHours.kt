// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * A daily window, by the hour, in which nothing is raised; it may run past midnight. Equal hours are no
 * window at all.
 */
public data class QuietHours(val fromHour: Int, val untilHour: Int) {
    public fun isQuiet(hour: Int): Boolean = when {
        fromHour == untilHour -> false
        fromHour < untilHour -> hour in fromHour until untilHour
        else -> hour >= fromHour || hour < untilHour
    }

    public companion object {
        public val Default: QuietHours = QuietHours(fromHour = 22, untilHour = 7)
    }
}
