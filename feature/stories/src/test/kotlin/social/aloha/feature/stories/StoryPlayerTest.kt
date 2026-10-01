// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.Story
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StoryPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()
    private var closed = false

    private val actions = object : StoryActions {
        override fun onSeen(story: Story) {
            asked += "seen:${story.id}"
        }

        override suspend fun audience(id: String): Audience = Audience(listOf(StatusSamples.bob), emptyList())

        override suspend fun react(id: String, reaction: String): Boolean = true.also { asked += "react:$id $reaction" }

        override suspend fun reply(id: String, text: String): Boolean = true.also { asked += "reply:$id $text" }

        override suspend fun delete(id: String): Boolean = true.also { asked += "delete:$id" }
    }

    private val own = StoryReel(StatusSamples.alice, listOf(Story("1", viewCount = 3, caption = "Sunset")), own = true)
    private val bobs = StoryReel(StatusSamples.bob, listOf(Story("2"), Story("3", seen = true)), own = false)

    private fun play(start: Int) {
        compose.setContent {
            AlohaTheme { StoryPlayer(listOf(own, bobs), start, actions, onProfile = {}, onClose = { closed = true }) }
        }
    }

    private fun story(): SemanticsNodeInteraction = compose.onNodeWithContentDescription("Story by", substring = true)

    private fun SemanticsNodeInteraction.act(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnIdle { action.action() }
        frame()
    }

    // the clock stands still, so a picture never times out on its own; each step is a frame
    private fun frame() = repeat(2) { compose.mainClock.advanceTimeByFrame() }

    @Test
    fun `a screen reader goes forward and back as taps do, and past the last story the player closes`() {
        compose.mainClock.autoAdvance = false
        play(start = 0)
        compose.onNodeWithContentDescription("Story by Alice Example. Sunset").assertIsDisplayed()
        story().act("Next story")
        compose.onNodeWithContentDescription("Story by Bob").assertIsDisplayed()
        story().act("Previous story")
        compose.onNodeWithContentDescription("Story by Alice Example. Sunset").assertIsDisplayed()
        repeat(3) { story().act("Next story") }
        assertTrue(closed)
        // the reader's own are never marked; anybody else's each time they show, which the rail's
        // model makes once
        assertEquals(listOf("seen:2", "seen:2", "seen:3"), asked)
    }

    @Test
    fun `an emoji goes to the poster with one tap, and says it went`() {
        compose.mainClock.autoAdvance = false
        play(start = 1)
        compose.onNodeWithText("🔥").performClick()
        frame()
        compose.onNodeWithText("Sent to Bob").assertIsDisplayed()
        assertEquals("react:2 🔥", asked.last())
        compose.onRoot().captureRoboImage("src/test/screenshots/story-player.png")
    }

    @Test
    fun `the reader's own say how many watched, and who`() {
        compose.mainClock.autoAdvance = false
        play(start = 0)
        compose.onRoot().captureRoboImage("src/test/screenshots/story-player-own.png")
        compose.onNodeWithText("Seen by 3").performClick()
        frame()
        // the open sheet pauses the story, so the clock may run
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Bob").assertIsDisplayed()
    }
}
