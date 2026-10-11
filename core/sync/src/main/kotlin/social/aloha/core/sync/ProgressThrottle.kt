// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.os.SystemClock

/**
 * How far a transfer has got, passed on to [onStep] twice a second at most, and on its last byte: each
 * step is a progress write, a notification and a redrawn screen, and Android drops the updates of an app
 * that posts them faster. A step that would show the same percentage is not passed on at all.
 */
internal class ProgressThrottle(private val onStep: (percent: Int, fraction: Float) -> Unit) {
    private var shownPercent = -1
    private var shownAt = 0L

    fun onSent(sent: Long, total: Long) {
        if (total <= 0) return
        val percent = (sent * PERCENT / total).toInt()
        val now = SystemClock.elapsedRealtime()
        if (percent == shownPercent || (now - shownAt < INTERVAL_MILLIS && sent < total)) return
        shownPercent = percent
        shownAt = now
        onStep(percent, sent.toFloat() / total)
    }

    private companion object {
        const val PERCENT = 100
        const val INTERVAL_MILLIS = 500L
    }
}
