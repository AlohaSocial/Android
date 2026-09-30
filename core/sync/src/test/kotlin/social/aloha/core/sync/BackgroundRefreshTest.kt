// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import social.aloha.core.model.PollFrequency
import social.aloha.core.sync.BackgroundRefresh.Pace

class BackgroundRefreshTest {
    @Test
    fun `the background refresh follows the fastest account, never below the fifteen minute floor`() {
        assertEquals(15.minutes, BackgroundRefresh.repeatFor(listOf(Pace(PollFrequency.Frequent))))
        assertEquals(
            15.minutes,
            BackgroundRefresh.repeatFor(listOf(Pace(PollFrequency.BatterySaver), Pace(PollFrequency.Normal))),
        )
        assertEquals(
            45.minutes,
            BackgroundRefresh.repeatFor(listOf(Pace(PollFrequency.BatterySaver), Pace(PollFrequency.Manual))),
        )
        assertNull(BackgroundRefresh.repeatFor(listOf(Pace(PollFrequency.Manual))))
        assertNull(BackgroundRefresh.repeatFor(emptyList()))
    }

    @Test
    fun `accounts their servers push to need the background refresh only once an hour`() {
        assertEquals(1.hours, BackgroundRefresh.repeatFor(listOf(Pace(PollFrequency.Normal, pushed = true))))
        assertEquals(
            15.minutes,
            BackgroundRefresh.repeatFor(listOf(Pace(PollFrequency.Normal, pushed = true), Pace(PollFrequency.Normal))),
        )
    }
}
