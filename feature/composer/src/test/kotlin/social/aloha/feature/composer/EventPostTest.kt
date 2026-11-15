// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.StatusEvent
import social.aloha.core.navigation.ComposerKey

/** A post that is an event, which Social takes from 0.26.279 on. */
@RunWith(RobolectricTestRunner::class)
internal class EventPostTest : ComposerTestSetup() {
    @Test
    fun `an event goes out with the opening post once it has a title and a start ending after it`() = runBlocking {
        val viewModel = open(version = "0.26.279")
        assertTrue(viewModel.await { it.ready }.events)
        viewModel.type(0, "Come along")
        val start = Instant.parse("2030-06-01T07:30:00Z")
        viewModel.event = StatusEvent(location = "North shore")
        assertEquals(EventProblem.NoTitle, viewModel.await { it.event != null }.event?.problem)
        assertFalse(viewModel.uiState.value.canPost)
        viewModel.event = StatusEvent("Beach clean-up", location = "North shore")
        assertEquals(EventProblem.NoStart, viewModel.await { it.event?.name != "" }.event?.problem)
        viewModel.event = StatusEvent("Beach clean-up", start, start, "North shore")
        assertEquals(EventProblem.EndBeforeStart, viewModel.await { it.event?.startsAt != null }.event?.problem)
        assertFalse(viewModel.uiState.value.canPost)
        viewModel.event = StatusEvent("Beach clean-up", start, start.plusSeconds(3_600), "North shore")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        val form = posts.sent.single().second
        assertEquals("Beach clean-up", form["event[name]"])
        assertEquals("2030-06-01T07:30:00Z", form["event[start_time]"])
        assertEquals("2030-06-01T08:30:00Z", form["event[end_time]"])
        assertEquals("North shore", form["event[location]"])
    }

    @Test
    fun `a redraft carries the event as posted, and an edit sends none`() = runBlocking {
        posts.event = Json.parseToJsonElement(
            """{"name":"Beach clean-up","start_time":"2020-06-01T07:30:00Z","end_time":null,""" +
                """"location":"North shore","status":"confirmed"}""",
        ).jsonObject
        val redraft = open(key = { ComposerKey(it, redraftId = "gone") }, version = "0.26.279")
        assertEquals("Beach clean-up", redraft.await { it.ready && it.event != null }.event?.name)
        redraft.await { it.canPost }
        redraft.onPost()
        redraft.await { it.done }
        val form = posts.sent.single().second
        assertEquals("Beach clean-up", form["event[name]"])
        assertEquals("2020-06-01T07:30:00Z", form["event[start_time]"])
        assertEquals("North shore", form["event[location]"])
        assertFalse("event[status]" in form)
        val edit = open(key = { ComposerKey(it, editId = "mine") }, version = "0.26.279")
        assertNull(edit.await { it.ready }.event)
        edit.await { it.canPost }
        edit.onPost()
        edit.await { it.done }
        assertFalse("event" in posts.edits.single())
    }

    @Test
    fun `a redraft of a cancelled event stays cancelled from 0,26,280 on`() = runBlocking {
        posts.event = cancelled()
        val redraft = open(key = { ComposerKey(it, redraftId = "gone") }, version = "0.26.280")
        val state = redraft.await { it.ready && it.event != null }
        assertEquals(StatusEvent.Status.Cancelled, state.event?.status)
        assertFalse(state.eventLeftOut)
        redraft.await { it.canPost }
        redraft.onPost()
        redraft.await { it.done }
        val form = posts.sent.single().second
        assertEquals("Beach clean-up", form["event[name]"])
        assertEquals("cancelled", form["event[status]"])
    }

    @Test
    fun `a redraft of a cancelled event before 0,26,280 is no event, and says so`() = runBlocking {
        posts.event = cancelled()
        val redraft = open(key = { ComposerKey(it, redraftId = "gone") }, version = "0.26.279")
        val state = redraft.await { it.ready }
        assertNull(state.event)
        assertTrue(state.eventLeftOut)
        redraft.await { it.canPost }
        redraft.onPost()
        redraft.await { it.done }
        assertFalse(posts.sent.single().second.keys.any { it.startsWith("event") })
    }

    private fun cancelled() = Json.parseToJsonElement(
        """{"name":"Beach clean-up","start_time":"2030-06-01T07:30:00Z","end_time":null,""" +
            """"location":"North shore","status":"cancelled"}""",
    ).jsonObject

    @Test
    fun `a post is an event or has a poll, never both`() = runBlocking {
        val viewModel = open(version = "0.26.279")
        viewModel.await { it.ready }
        viewModel.type(0, "Which beach?")
        viewModel.poll.value = PollUi(options = listOf("North", "South"))
        viewModel.event = StatusEvent("Beach clean-up", Instant.parse("2030-06-01T07:30:00Z"))
        assertFalse(viewModel.await { it.poll != null && it.event != null }.canPost)
        viewModel.poll.value = null
        assertTrue(viewModel.await { it.poll == null && it.canPost }.event != null)
    }

    @Test
    fun `an event is kept with the draft and comes back with it`() = runBlocking {
        val viewModel = open(version = "0.26.279")
        viewModel.await { it.ready }
        viewModel.type(0, "Come along")
        val event = StatusEvent("Beach clean-up", Instant.parse("2030-06-01T07:30:00Z"), location = "North shore")
        viewModel.event = event
        val kept = withTimeout(10.seconds) {
            outbox.observe(accounts.all().single().id).first { list -> list.any { it.post.event != null } }
        }.single()
        assertEquals(event, kept.post.event)
        val again = open(key = { ComposerKey(it, draftId = kept.id) }, version = "0.26.279")
        assertEquals(event, again.await { it.ready }.event)
    }

    @Test
    fun `a server before 0,26,279 is offered no event, and a draft's goes`() = runBlocking {
        val viewModel = open(version = "0.26.279")
        viewModel.await { it.ready }
        viewModel.type(0, "Come along")
        viewModel.event = StatusEvent("Beach clean-up", Instant.parse("2030-06-01T07:30:00Z"))
        val kept = withTimeout(10.seconds) {
            outbox.observe(accounts.all().single().id).first { list -> list.any { it.post.event != null } }
        }.single()
        val older = open(key = { ComposerKey(it, draftId = kept.id) }, version = "0.26.278")
        val state = older.await { it.ready }
        assertFalse(state.events)
        assertNull(state.event)
    }
}
