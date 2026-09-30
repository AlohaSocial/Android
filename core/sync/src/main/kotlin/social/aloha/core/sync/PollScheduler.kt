// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import social.aloha.core.model.PollFrequency

/** How lately the person touched the open app, and the base interval each gives an account. */
public enum class Attention(internal val active: Duration, internal val other: Duration) {
    Interacting(30.seconds, 180.seconds),
    IdleShort(60.seconds, 300.seconds),
    IdleLong(120.seconds, 600.seconds),
    ;

    public companion object {
        public fun after(idle: Duration): Attention = when {
            idle < 2.minutes -> Interacting
            idle < 10.minutes -> IdleShort
            else -> IdleLong
        }
    }
}

/** What one poll asks for: everything, or only what drives the badge and notifications. */
public enum class PollScope { Full, NotificationsOnly }

/**
 * The foreground interval table, as a pure function so it is tested without waiting. The active account
 * is asked more often than the others, an idle person less often, and a device saving power half as often.
 */
public data class PollScheduler(
    val activeAccount: Boolean,
    val attention: Attention,
    val frequency: PollFrequency = PollFrequency.Normal,
    val powerSave: Boolean = false,
    val metered: Boolean = false,
    val wifiOnly: Boolean = false,
) {
    /** Null when nothing is asked on a timer: the person refreshes by hand. */
    val interval: Duration?
        get() {
            val multiplier = frequency.multiplier ?: return null
            val base = if (activeAccount) attention.active else attention.other
            return base * multiplier * if (powerSave) 2 else 1
        }

    /** On a metered network with Wi-Fi-only sync, notifications are still asked for: a wrong badge is worse. */
    val scope: PollScope
        get() = if (metered && wifiOnly) PollScope.NotificationsOnly else PollScope.Full
}
