// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import android.content.Intent
import android.icu.util.ULocale
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import social.aloha.core.model.StatusEvent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class EventCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val start = Instant.parse("2026-11-02T18:00:00Z")
    private val end = Instant.parse("2026-11-02T21:00:00Z")
    private val before = Instant.parse("2026-10-01T12:00:00Z")
    private val links = mutableListOf<RichLinkTarget>()

    private val actions = object : StatusActions {
        override fun onOpen(statusId: String) = Unit
        override fun onProfile(accountId: String) = Unit
        override fun onLink(target: RichLinkTarget) {
            links += target
        }
        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onReply(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
    }

    private fun show(event: StatusEvent, now: Instant = before, url: String? = "https://events.example/e/1") =
        compose.setContent { EventCard(event, url, now, actions) }

    // ICU spaces its times with narrow no-break and thin spaces
    private fun time(end: Instant?) = eventTime(start, end, ZoneOffset.UTC, ULocale.US).replace(Regex("\\p{Zs}"), " ")

    @Test
    fun `an event says its weekday, and its day once when it ends on the day it starts`() {
        assertEquals("Mon, Nov 2, 2026, 6:00 PM", time(null))
        assertEquals("Mon, Nov 2, 2026, 6:00 – 9:00 PM", time(Instant.parse("2026-11-02T21:00:00Z")))
        assertEquals(
            "Mon, Nov 2, 2026, 6:00 PM – Tue, Nov 3, 2026, 9:00 AM",
            time(Instant.parse("2026-11-03T09:00:00Z")),
        )
    }

    @Test
    fun `a new time zone shows the event in it from the next minute`() {
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            var now by mutableStateOf(before)
            compose.setContent { EventCard(StatusEvent("Harbour clean-up", start, end), null, now, actions) }
            compose.onNodeWithText("Nov 2", substring = true).assertExists()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            now = before.plusSeconds(60)
            compose.onNodeWithText("Nov 3", substring = true).assertExists()
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun `add to calendar opens the calendar with the event filled in`() {
        show(StatusEvent("Harbour clean-up", start, end, "Pier 4"), url = "https://tube.example/w/1")
        compose.onNodeWithText("Add to calendar").performClick()
        val sent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_INSERT, sent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, sent.data)
        assertEquals(start.toEpochMilli(), sent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(end.toEpochMilli(), sent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
        assertEquals("Harbour clean-up", sent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Pier 4", sent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertEquals("https://tube.example/w/1", sent.getStringExtra(CalendarContract.Events.DESCRIPTION))
    }

    @Test
    fun `an event without a start offers no calendar`() {
        show(StatusEvent("Someday", null, null, "Pier 4"), url = null)
        compose.onNodeWithText("Pier 4").assertExists()
        compose.onNodeWithText("Add to calendar").assertDoesNotExist()
        compose.onNodeWithText("Event page").assertDoesNotExist()
    }

    @Test
    fun `an event is still to come, going on, begun with no end given, or over`() {
        val ends = StatusEvent("x", start, end)
        val open = StatusEvent("x", start, null)
        assertEquals(EventPhase.Upcoming, eventPhase(ends, before))
        assertEquals(EventPhase.Ongoing, eventPhase(ends, start))
        assertEquals(EventPhase.Ended, eventPhase(ends, end))
        assertEquals(EventPhase.Started, eventPhase(open, end))
        assertEquals(null, eventPhase(StatusEvent("x"), before))
    }

    @Test
    fun `a cancelled event says so and offers no calendar, its page still`() {
        show(StatusEvent("Harbour clean-up", start, end, "Pier 4", StatusEvent.Status.Cancelled))
        compose.onNodeWithText("Cancelled").assertExists()
        compose.onNodeWithText("Add to calendar").assertDoesNotExist()
        compose.onNodeWithText("Event page").performClick()
        assertEquals(listOf<RichLinkTarget>(RichLinkTarget.Web("https://events.example/e/1")), links)
    }

    @Test
    fun `a tentative event going on says both, and still goes into the calendar`() {
        show(StatusEvent("Harbour clean-up", start, end, "Pier 4", StatusEvent.Status.Tentative), now = start)
        compose.onNodeWithText("Tentative · Happening now").assertExists()
        compose.onNodeWithText("Add to calendar").assertExists()
    }

    @Test
    fun `an event that is over says so and offers no calendar`() {
        show(StatusEvent("Harbour clean-up", start, end), now = end)
        compose.onNodeWithText("Ended").assertExists()
        compose.onNodeWithText("Add to calendar").assertDoesNotExist()
    }

    @Test
    fun `an event begun with no end given says started, not over`() {
        show(StatusEvent("Harbour clean-up", start, null), now = end)
        compose.onNodeWithText("Started").assertExists()
        compose.onNodeWithText("Add to calendar").assertExists()
    }

    @Test
    fun `only a web address is offered as the event's page`() {
        show(StatusEvent("Harbour clean-up", start, end), url = "javascript:alert(1)")
        compose.onNodeWithText("Event page").assertDoesNotExist()
    }
}
