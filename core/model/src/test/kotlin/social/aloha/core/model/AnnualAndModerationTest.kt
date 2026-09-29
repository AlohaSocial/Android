// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnualAndModerationTest {
    @Test
    fun `an admin account's standing is the strongest thing true of it`() {
        val suspended = AdminAccount("1", "bob", domain = "other.example", silenced = true, suspended = true)
        assertEquals(AdminStanding.Suspended, suspended.standing)
        assertFalse(suspended.isLocal)
        assertEquals("@bob@other.example", suspended.handle)

        val silenced = AdminAccount("2", "eve", silenced = true)
        assertEquals(AdminStanding.Silenced, silenced.standing)
        assertTrue(silenced.isLocal)
        assertEquals("@eve", silenced.handle)

        assertEquals(AdminStanding.Sensitized, AdminAccount("3", "ada", sensitized = true).standing)
        assertEquals(AdminStanding.Active, AdminAccount("4", "ada").standing)
    }

    @Test
    fun `a report is assigned only when somebody took it`() {
        assertFalse(AdminReport("7").isAssigned)
        assertTrue(AdminReport("7", assignedAccount = Account("1", "ada", "ada")).isAssigned)
    }

    @Test
    fun `an activity week starts at its Unix second and keys by it`() {
        val week = InstanceActivityWeek(1_767_139_200)
        assertEquals(Instant.ofEpochSecond(1_767_139_200), week.startsAt)
        assertEquals(1_767_139_200L, week.id)
    }

    @Test
    fun `a published domain block keys off its digest, the domain when there is none`() {
        assertEquals("abc", PublicDomainBlock("bad.example", digest = "abc").id)
        assertEquals("bad.example", PublicDomainBlock("bad.example").id)
    }

    @Test
    fun `the year's totals and busiest month come from the time series`() {
        val data =
            AnnualReportData(timeSeries = listOf(AnnualMonth(1, 3, 1), AnnualMonth(2, 11, 0), AnnualMonth(3, 0, 0)))
        assertEquals(14, data.totalStatuses)
        assertEquals(1, data.totalFollowers)
        assertEquals(2, data.busiestMonth?.month)
        assertNull(AnnualReportData(timeSeries = listOf(AnnualMonth(1))).busiestMonth)
    }

    @Test
    fun `the three best posts are distinct ids, in order, without the absent one`() {
        assertEquals(listOf("110"), AnnualTopStatuses(byReblogs = "110", byFavourites = "110").ids)
        assertEquals(listOf("1", "2"), AnnualTopStatuses("1", "2", "1").ids)
    }

    @Test
    fun `a wrapped report finds the status behind a top-status id`() {
        val wrapped = WrappedAnnualReports()
        assertNull(wrapped.status(null))
        assertNull(wrapped.status("9"))
    }

    @Test
    fun `unknown wire values are unknown, never an exception`() {
        assertEquals(AnnualArchetype.Oracle, AnnualArchetype.fromWire("oracle"))
        assertEquals(AnnualArchetype.Unknown, AnnualArchetype.fromWire("sphinx"))
        assertEquals(AnnualReportState.Available, AnnualReportState.fromWire("available"))
        assertEquals(AnnualReportState.Unknown, AnnualReportState.fromWire("melting"))
    }
}
