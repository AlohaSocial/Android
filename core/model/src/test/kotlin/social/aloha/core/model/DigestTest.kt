// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DigestTest {
    private val digest = Digest(hours = listOf(8, 18))

    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 4, hour, minute, 0, 0, ZoneOffset.UTC)

    @Test
    fun `the next digest time is the first chosen hour still ahead, else tomorrow's first`() {
        assertEquals(at(18), digest.nextAfter(at(9, 30), quiet = null))
        assertEquals(at(8).plusDays(1), digest.nextAfter(at(19), quiet = null))
        // on the hour itself the time has passed
        assertEquals(at(18), digest.nextAfter(at(8), quiet = null))
    }

    @Test
    fun `a digest time inside quiet hours waits until they end`() {
        assertEquals(at(20), digest.nextAfter(at(9), QuietHours(fromHour = 17, untilHour = 20)))
        // quiet hours past midnight: the morning digest moves to their end
        assertEquals(at(9), Digest(listOf(6)).nextAfter(at(1), QuietHours(fromHour = 22, untilHour = 9)))
        // two times that both land on the end of quiet hours are one
        assertEquals(at(20), Digest(listOf(17, 19)).nextAfter(at(9), QuietHours(fromHour = 17, untilHour = 20)))
    }

    @Test
    fun `no hours at all fall back to the default times`() {
        assertEquals(at(18), Digest(emptyList()).nextAfter(at(9), quiet = null))
    }
}
