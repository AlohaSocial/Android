// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.timeline.PageOutcome
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey

/**
 * Home as Nextcloud Social serves it since it leaves posts out after choosing the page: a page can come
 * back short or empty and still name the next one.
 */
@RunWith(RobolectricTestRunner::class)
class TimelineHiddenPostsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val home = TimelineKey.home()
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun close() {
        fixture.close()
        servers.forEach { it.close() }
    }

    private suspend fun rows(account: SignedInAccount) = fixture.timelines.observe(account, home).first()

    private fun List<TimelineRow>.ids() = map { it.id }

    @Test
    fun `a home page the server emptied after paging is read past, and the posts under it arrive`() = runBlocking {
        val timeline = NumberedTimeline().apply {
            newest = 60
            hidden = { it in 21..40 }
        }
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val account = fixture.signIn(server.url("/"))
        val first = fixture.timelines.refresh(account, home, RefreshPlan.of(false, null)) as PageOutcome.Loaded
        val older = fixture.timelines.older(account, home, first.nextCursor, oldestId = "41") as PageOutcome.Loaded
        assertEquals((20 downTo 1).map(Int::toString), older.arrived)
        assertFalse(older.reachedEnd)
        assertEquals((60 downTo 41).map(Int::toString) + (20 downTo 1).map(Int::toString), rows(account).ids())
    }

    @Test
    fun `a gap page the server shortened after paging leaves the gap open until the rows below are reached`() =
        runBlocking {
            val timeline = NumberedTimeline().apply { newest = 100 }
            val server = MockWebServer().apply {
                dispatcher = timeline
                start()
            }.also { servers += it }
            val account = fixture.signIn(server.url("/"))
            fixture.timelines.refresh(account, home, RefreshPlan.of(false, null))
            timeline.newest = 150
            timeline.hidden = { it in 115..130 }
            fixture.timelines.refresh(account, home, RefreshPlan.of(true, "100"))
            // the first page under the gap is 130 to 111, of which only 114 to 111 are left
            fixture.timelines.fillGap(account, home, "gap:131", aboveId = "131")
            val gap = rows(account).single { it is TimelineRow.Gap }.id
            fixture.timelines.fillGap(account, home, gap, aboveId = "111")
            assertEquals(
                (150 downTo 131).map(Int::toString) + (114 downTo 81).map(Int::toString),
                rows(account).ids(),
            )
        }

    @Test
    fun `a gap behind more hidden posts than one fill reads goes on where the last fill stopped`() = runBlocking {
        val timeline = NumberedTimeline().apply { newest = 100 }
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val account = fixture.signIn(server.url("/"))
        fixture.timelines.refresh(account, home, RefreshPlan.of(false, null))
        timeline.newest = 300
        timeline.hidden = { it in 131..280 }
        fixture.timelines.refresh(account, home, RefreshPlan.of(true, "100"))
        // 150 hidden posts: one fill reads five pages of them and keeps the gap
        repeat(5) {
            val ids = rows(account).ids()
            val gap = ids.firstOrNull { it.startsWith("gap:") } ?: return@repeat
            fixture.timelines.fillGap(account, home, gap, aboveId = ids[ids.indexOf(gap) - 1])
        }
        assertEquals((300 downTo 281).map(Int::toString) + (130 downTo 81).map(Int::toString), rows(account).ids())
    }

    @Test
    fun `a refresh whose page the server emptied does not read on below the top`() = runBlocking {
        val timeline = NumberedTimeline().apply { newest = 100 }
        val server = MockWebServer().apply {
            dispatcher = timeline
            start()
        }.also { servers += it }
        val account = fixture.signIn(server.url("/"))
        fixture.timelines.refresh(account, home, RefreshPlan.of(false, null))
        timeline.newest = 120
        timeline.hidden = { it > 100 }
        val asked = server.requestCount
        fixture.timelines.refresh(account, home, RefreshPlan.of(true, "100"))
        assertEquals(1, server.requestCount - asked)
        assertEquals((100 downTo 81).map(Int::toString), rows(account).ids())
    }
}
