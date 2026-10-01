// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Status
import social.aloha.core.testing.StatusSamples

/** A translation takes the post's place on every card, and the original comes back on asking. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StatusCardTranslationTest {
    @get:Rule
    val compose = createComposeRule()

    private val screenItems = mutableListOf<StatusMenuItem>()

    private val actions = object : StatusActions {
        override fun onOpen(statusId: String) = Unit
        override fun onProfile(accountId: String) = Unit
        override fun onLink(target: RichLinkTarget) = Unit
        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onReply(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) {
            screenItems += item
        }
    }

    private class Translations(private val offered: Boolean, private val answer: TranslationUi) : StatusTranslations {
        val states = mutableStateMapOf<String, TranslationUi>()

        override fun offers(row: StatusRowUi) = offered
        override fun stateOf(statusId: String) = states[statusId]
        override fun translate(row: StatusRowUi) {
            states[row.statusId] = answer
        }
        override fun showOriginal(statusId: String) {
            states.remove(statusId)
        }
    }

    private fun show(status: Status, translations: StatusTranslations) {
        compose.setContent {
            AlohaTheme {
                CompositionLocalProvider(LocalStatusTranslations provides translations) {
                    val row = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme()).map(status, "1")
                    StatusCard(row, StatusSamples.NOW, SensitiveMediaPolicy.Blur, actions)
                }
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
    fun `the translation is read in place of the post, with who translated it, until the original is asked for`() {
        show(StatusSamples.linked, Translations(true, TranslationUi.Done("<p>Lesenswert</p>", null, "DeepL")))
        perform("Translate")
        compose.onNodeWithContentDescription("Lesenswert", substring = true).assertExists()
        compose.onNodeWithContentDescription("Translated by DeepL through your server", substring = true)
            .assertExists()
        assertFalse("Translate" in labels())
        compose.onNodeWithText("Show original", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Worth reading", substring = true).assertExists()
        assertTrue("Translate" in labels())
        assertEquals(emptyList<StatusMenuItem>(), screenItems)
    }

    @Test
    fun `a refusal is told in the server's words, and dismissed`() {
        show(StatusSamples.linked, Translations(true, TranslationUi.Failed("no translation provider is configured")))
        perform("Translate")
        compose.onNodeWithText(
            "Can’t translate right now: no translation provider is configured",
            useUnmergedTree = true,
        ).assertExists()
        perform("Show original")
        compose.onNodeWithText("Dismiss", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a post not worth translating is not offered, whatever the screen says`() {
        show(StatusSamples.linked, Translations(false, TranslationUi.Working))
        assertFalse("Translate" in labels())
        assertFalse("Show original" in labels())
    }
}
