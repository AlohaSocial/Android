// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.LocalStatusTranslations
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.StatusTranslations
import social.aloha.core.ui.TranslationUi

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ThreadTranslationTest {
    @get:Rule
    val compose = createComposeRule()

    private val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
    private val asked = mutableListOf<String>()
    private val originals = mutableListOf<String>()

    // posts in Japanese are foreign to the reader; the rest are not
    private val translations = object : StatusTranslations {
        val states = mutableMapOf<String, TranslationUi>()

        override fun offers(row: StatusRowUi) = row.language == "ja"

        override fun stateOf(statusId: String) = states[statusId]

        override fun translate(row: StatusRowUi) {
            asked += row.statusId
            states[row.statusId] = TranslationUi.Working
        }

        override fun showOriginal(statusId: String) {
            originals += statusId
            states.remove(statusId)
        }
    }

    private fun post(id: String, language: String) = ThreadItem.Post(
        mapper.map(StatusSamples.post().copy(id = id, language = language), "1", showContext = false),
        focused = id == "f",
        depth = if (id == "f") 0 else 1,
    )

    @Test
    fun `one tap translates foreign posts and later replies, and one shown in its original stays so`() {
        var items by mutableStateOf(listOf(post("f", "ja"), post("r1", "en"), post("r2", "ja")))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AlohaTheme {
                CompositionLocalProvider(LocalStatusTranslations provides translations) {
                    ThreadMenu(listOfNotNull(rememberThreadTranslation(items)))
                }
            }
        }
        assertEquals(emptyList<String>(), asked)
        compose.onNodeWithContentDescription("More for this thread").performClick()
        compose.onNodeWithText("Translate thread").performClick()
        compose.waitForIdle()
        assertEquals(listOf("f", "r2"), asked)
        items = items + post("r3", "ja")
        compose.waitForIdle()
        assertEquals(listOf("f", "r2", "r3"), asked)
        translations.showOriginal("r2")
        restoration.emulateSavedInstanceStateRestore()
        items = items + post("r4", "en")
        compose.waitForIdle()
        assertEquals(listOf("f", "r2", "r3"), asked)
        originals.clear()
        compose.onNodeWithContentDescription("More for this thread").performClick()
        compose.onNodeWithText("Show originals").performClick()
        compose.waitForIdle()
        assertEquals(listOf("f", "r1", "r2", "r3", "r4"), originals)
    }
}
