// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.WarningReveal
import social.aloha.core.testing.StatusSamples

/** In a thread, opening one content warning opens the same warning on the other posts, as Reading allows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WarningRevealTest {
    @get:Rule
    val compose = createComposeRule()

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
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
    }

    private val cards = SemanticsMatcher("has custom actions") { it.config.contains(SemanticsActions.CustomActions) }

    private fun firstAction(index: Int) = compose.onAllNodes(cards)[index].fetchSemanticsNode()
        .config[SemanticsActions.CustomActions].first().label

    private fun show(mode: WarningReveal) {
        val second = StatusSamples.spoiler.copy(id = "99", spoilerText = "re: Spoilers for the finale")
        compose.setContent {
            AlohaTheme {
                val mapper = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme())
                CompositionLocalProvider(LocalWarningReveals provides WarningReveals(mode)) {
                    Column {
                        listOf(StatusSamples.spoiler, second).forEach {
                            StatusCard(mapper.map(it, "1"), StatusSamples.NOW, SensitiveMediaPolicy.Blur, actions)
                        }
                    }
                }
            }
        }
    }

    private fun openFirst() {
        compose.runOnIdle {
            compose.onAllNodes(cards)[0].fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == "Show post" }.action()
        }
        compose.waitForIdle()
    }

    @Test
    fun `the same warning by the same author opens with the first`() {
        show(WarningReveal.SameAuthor)
        openFirst()
        assertEquals("Hide post", firstAction(1))
    }

    @Test
    fun `with Never, each warning waits for its own tap`() {
        show(WarningReveal.Never)
        openFirst()
        assertEquals("Hide post", firstAction(0))
        assertEquals("Show post", firstAction(1))
    }
}
