// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Reaction

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReactionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val reacted = mutableListOf<Pair<String, Boolean>>()

    private fun show(reactions: List<Reaction>, emoji: List<CustomEmoji>) = compose.setContent {
        CompositionLocalProvider(LocalReactionEmoji provides mutableStateOf(emoji)) {
            Reactions(reactions, canReact = true) { name, add -> reacted += name to add }
        }
    }

    @Test
    fun `the server's own emoji are offered after the Unicode ones, sent by shortcode`() {
        show(emptyList(), listOf(CustomEmoji("blobwave", "https://x.test/e/blobwave.png")))
        compose.onNodeWithContentDescription("Add a reaction").performClick()
        compose.onNodeWithContentDescription("blobwave").performClick()
        assertEquals(listOf(":blobwave:" to true), reacted)
    }

    @Test
    fun `a custom emoji the reader gave already is not offered again`() {
        show(
            listOf(Reaction(":blobwave:", 1, me = true, url = "https://x.test/e/blobwave.png")),
            listOf(CustomEmoji("blobwave", "https://x.test/e/blobwave.png"), CustomEmoji("blobcat")),
        )
        compose.onNodeWithContentDescription("Add a reaction").performClick()
        compose.onNodeWithContentDescription("blobcat").assertExists()
        // one without a picture to be had says its shortcode instead
        compose.onNodeWithText(":blobcat:").assertExists()
        // the chip already given still shows its picture, named by its shortcode
        assertEquals(1, compose.onAllNodesWithContentDescription(":blobwave:").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("blobwave").assertDoesNotExist()
    }
}
