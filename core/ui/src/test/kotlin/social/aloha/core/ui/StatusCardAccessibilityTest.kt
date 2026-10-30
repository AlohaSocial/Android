// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Status
import social.aloha.core.model.StatusEvent
import social.aloha.core.testing.StatusSamples

/** What a screen reader can do on a card: whatever a tap on the card's own controls can. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StatusCardAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val votes = mutableListOf<List<Int>>()
    private val histories = mutableListOf<String>()
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
        override fun onVote(row: StatusRowUi, choices: List<Int>) {
            votes += choices
        }
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
        override fun onHistory(row: StatusRowUi) {
            histories += row.statusId
        }
    }

    private fun show(status: Status) {
        compose.setContent {
            AlohaTheme {
                val row = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme()).map(status, "1")
                StatusCard(row, StatusSamples.NOW, SensitiveMediaPolicy.Blur, actions)
            }
        }
    }

    private fun card() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription).and(
            SemanticsMatcher("has custom actions") { it.config.contains(SemanticsActions.CustomActions) },
        ),
    )

    private fun labels(): List<String> =
        card().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun perform(label: String) {
        compose.runOnIdle {
            card().fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action()
        }
        compose.waitForIdle()
    }

    @Test
    fun `a post behind a content warning can be opened, and only then read`() {
        show(StatusSamples.spoiler)
        assertTrue(labels().first() == "Show post")
        perform("Show post")
        assertTrue(labels().contains("Hide post"))
        compose.onNodeWithContentDescription("Aloha from the", substring = true).assertExists()
    }

    @Test
    fun `a poll can be answered option by option, then voted on`() {
        show(StatusSamples.poll)
        assertTrue("Vote" !in labels())
        perform("Choose Kauai")
        assertTrue(labels().contains("Vote"))
        perform("Vote")
        assertEquals(listOf(listOf(1)), votes)
    }

    @Test
    fun `an event is read out with the post, and goes into the calendar from the row`() {
        val event = StatusEvent("Harbour clean-up", Instant.parse("2026-11-02T18:00:00Z"), null, "Pier 4")
        show(StatusSamples.post().copy(event = event))
        compose.onNodeWithContentDescription("Harbour clean-up", substring = true).assertExists()
        compose.onNodeWithContentDescription("Pier 4", substring = true).assertExists()
        perform("Add to calendar")
        val sent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_INSERT, sent.action)
    }

    @Test
    fun `a cancelled event is read out as cancelled, with no calendar but its page`() {
        val event = StatusEvent(
            "Harbour clean-up",
            Instant.parse("2026-11-02T18:00:00Z"),
            null,
            "Pier 4",
            StatusEvent.Status.Cancelled,
        )
        show(StatusSamples.post().copy(url = "https://events.example/e/1", event = event))
        compose.onNodeWithContentDescription("Harbour clean-up, Cancelled", substring = true).assertExists()
        assertTrue("Add to calendar" !in labels())
        assertTrue("Open event page" in labels())
    }

    @Test
    fun `an event's sign-up is taken part in from the row`() {
        val join = "https://mobilizon.example/events/1/join"
        val event = StatusEvent("Harbour clean-up", Instant.parse("2099-11-02T18:00:00Z"), participationUrl = join)
        show(StatusSamples.post().copy(event = event))
        perform("Take part")
        assertEquals(listOf<RichLinkTarget>(RichLinkTarget.Web(join)), links)
    }

    @Test
    fun `an event with no start offers no calendar`() {
        show(StatusSamples.post().copy(event = StatusEvent("Someday", null, null, "Pier 4")))
        assertTrue("Add to calendar" !in labels())
    }

    @Test
    fun `an edited post offers its edit history`() {
        show(StatusSamples.direct)
        perform("Edit history")
        assertEquals(listOf("21"), histories)
    }

    @Test
    fun `a post never edited has no history to offer`() {
        show(StatusSamples.post())
        assertTrue("Edit history" !in labels())
    }
}
