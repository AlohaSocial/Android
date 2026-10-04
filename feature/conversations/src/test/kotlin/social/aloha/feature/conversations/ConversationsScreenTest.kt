// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.conversations

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.Conversation
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ConversationsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = ConversationsActions(
        onOpen = { asked += "open:${it.id}" },
        onDelete = { asked += "delete:$it" },
        onReadAll = { asked += "read-all" },
        onMore = {},
        onNew = { asked += "new" },
        onBack = {},
    )

    private val state = ConversationsUiState(
        conversations = listOf(
            Conversation(
                "c1",
                listOf(StatusSamples.bob),
                unread = true,
                lastStatus = StatusSamples.post("<p>Surf at six?</p>"),
            ),
            Conversation("c2", listOf(StatusSamples.alice), lastStatus = StatusSamples.spoiler),
        ),
        loading = false,
        done = true,
    )

    @Test
    fun `a conversation opens, is removed after asking, and everything is marked read`() {
        compose.setContent { AlohaTheme { ConversationsScreen(state, actions, {}, now = StatusSamples.NOW) } }
        compose.onNodeWithText("Bob").performClick()
        compose.onNodeWithContentDescription("Remove conversation with Bob").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithContentDescription("Mark all read").performClick()
        compose.onNodeWithText("New message", useUnmergedTree = true).performClick()
        assertEquals(listOf("open:c1", "delete:c1", "read-all", "new"), asked)
    }

    @Test
    fun `a message behind a content warning shows only the warning`() {
        compose.setContent { AlohaTheme { ConversationsScreen(state, actions, {}, now = StatusSamples.NOW) } }
        compose.onNodeWithText("Spoilers for the finale").assertExists()
    }

    @Test
    fun conversations() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { ConversationsScreen(state, actions, {}, now = StatusSamples.NOW) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/conversations.png")
    }

    @Test
    @Config(fontScale = 2f)
    fun conversationsLargeFont() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { ConversationsScreen(state, actions, {}, now = StatusSamples.NOW) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/conversations-font200.png")
    }

    @Test
    fun `a new message offers mutual follows until a name is typed`() {
        val mutuals = NewMessageUiState(mutuals = listOf(StatusSamples.bob), loading = false)
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { NewMessageScreen(mutuals, {}, { asked += "to:${it.acct}" }, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/new-message.png")
        compose.onNodeWithText("People you follow who follow you back").assertExists()
        compose.onNodeWithText("Bob").performClick()
        assertEquals(listOf("to:bob@other.social"), asked)
    }

    @Test
    fun `a name typed shows who it finds instead of the mutual follows`() {
        val typed = NewMessageUiState(
            query = "ali",
            mutuals = listOf(StatusSamples.bob),
            found = listOf(StatusSamples.alice),
            loading = false,
        )
        compose.setContent { AlohaTheme { NewMessageScreen(typed, {}, {}, {}) } }
        compose.onNodeWithText("Alice Example").assertExists()
        compose.onNodeWithText("Bob").assertDoesNotExist()
        compose.onNodeWithText("People you follow who follow you back").assertDoesNotExist()
    }
}
