// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import social.aloha.core.datastore.FocusBefore
import social.aloha.core.datastore.FocusPreferences
import social.aloha.core.datastore.NotificationPreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.model.Digest

/**
 * The calm set, on or off together on this device: the numbers off, boosts folded, trends hidden and
 * notifications held for the digest. Turned off, each goes back to what it was before, so a setting the
 * reader had chosen already stays chosen; one the reader changed while focus was on, in Reading or the
 * notification settings, stays as they changed it. One change at a time: two taps at once cannot both
 * take the calm set for what was there before.
 */
@Singleton
public class FocusMode @Inject constructor(
    private val reading: ReadingPreferences,
    private val notifications: NotificationPreferences,
    private val focus: FocusPreferences,
) {
    public val on: Flow<Boolean> = focus.before.map { it != null }.distinctUntilChanged()

    private val changing = Mutex()

    public suspend fun set(on: Boolean): Unit = changing.withLock {
        val before = focus.before.first()
        if (on == (before != null)) return@withLock
        val style = reading.style.first()
        val digest = notifications.digest.first()
        if (before == null) {
            focus.remember(FocusBefore(style.showCounts, style.boostCarousel, style.showTrends, digest != null))
            reading.setStyle(style.copy(showCounts = false, boostCarousel = true, showTrends = false))
            if (digest == null) notifications.setDigest(Digest.Default)
        } else {
            // only what is still as focus mode set it goes back
            reading.setStyle(
                style.copy(
                    showCounts = if (style.showCounts) true else before.counts,
                    boostCarousel = if (style.boostCarousel) before.carousel else false,
                    showTrends = if (style.showTrends) true else before.trends,
                ),
            )
            if (!before.digest && digest == Digest.Default) notifications.setDigest(null)
            focus.remember(null)
        }
    }
}
