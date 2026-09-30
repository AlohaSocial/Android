// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import social.aloha.core.model.PollFrequency

class PollSchedulerTest {
    @Test
    fun `the active account is asked more often than the others, and an idle person less often`() {
        val table = Attention.entries.map { attention ->
            listOf(true, false).map { PollScheduler(activeAccount = it, attention = attention).interval }
        }
        assertEquals(
            listOf(listOf(30.seconds, 180.seconds), listOf(60.seconds, 300.seconds), listOf(120.seconds, 600.seconds)),
            table,
        )
    }

    @Test
    fun `idleness is read from the time since the last touch`() {
        assertEquals(Attention.Interacting, Attention.after(119.seconds))
        assertEquals(Attention.IdleShort, Attention.after(2.minutes))
        assertEquals(Attention.IdleLong, Attention.after(10.minutes))
    }

    @Test
    fun `the chosen pace and power saving scale the table, and manual asks nothing on a timer`() {
        val active = PollScheduler(activeAccount = true, attention = Attention.Interacting)
        assertEquals(15.seconds, active.copy(frequency = PollFrequency.Frequent).interval)
        assertEquals(90.seconds, active.copy(frequency = PollFrequency.BatterySaver).interval)
        assertEquals(60.seconds, active.copy(powerSave = true).interval)
        assertNull(active.copy(frequency = PollFrequency.Manual).interval)
    }

    @Test
    fun `wifi-only sync on a metered network still asks for notifications`() {
        val scheduler = PollScheduler(activeAccount = true, attention = Attention.Interacting)
        assertEquals(PollScope.Full, scheduler.copy(metered = true).scope)
        assertEquals(PollScope.Full, scheduler.copy(wifiOnly = true).scope)
        assertEquals(PollScope.NotificationsOnly, scheduler.copy(metered = true, wifiOnly = true).scope)
    }
}
