// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import java.io.File
import java.util.Optional
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.intelligence.Intelligence
import social.aloha.core.intelligence.Observations
import social.aloha.core.intelligence.PictureReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
internal class AltTextDraftsTest : ComposerTestSetup() {
    @get:Rule
    val compose = createComposeRule()

    private val reader = object : PictureReader {
        override suspend fun observe(file: File) = Observations(subjects = listOf(file.nameWithoutExtension))
    }

    private fun picture(name: String) =
        Picked(File(context.filesDir, "$name.jpg").apply { writeBytes(byteArrayOf(1)) }, "$name.jpg", "image/jpeg")

    private suspend fun drafting(): ComposerViewModel {
        intelligence = Intelligence(context, Optional.empty(), Optional.of(reader))
        val viewModel = open()
        viewModel.await { it.ready }
        withTimeout(10.seconds) { viewModel.altText.state.first { it.on } }
        return viewModel
    }

    @Test
    fun `every undescribed picture is drafted in turn, marked, and waits to be checked`() = runBlocking {
        val viewModel = drafting()
        val beach = checkNotNull(viewModel.attachments.addPrepared(0, picture("beach"), ""))
        val reef = checkNotNull(viewModel.attachments.addPrepared(0, picture("reef"), "Written by hand"))
        viewModel.altText.draftAll()
        val drafted = viewModel.await { state -> state.attachments.flatten().all { it.description.isNotBlank() } }
            .attachments.flatten().associate { it.id to it.description }
        assertEquals("Beach. (AI generated)", drafted[beach])
        assertEquals("Written by hand", drafted[reef])
        assertEquals(listOf(beach), viewModel.altText.unchecked())
        viewModel.altText.checked(beach)
        assertTrue(viewModel.altText.unchecked().isEmpty())
    }

    @Test
    fun `a draft from the editor keeps its mark as drafted, and loses it once the writer edits it`() {
        val viewModel = runBlocking { drafting() }
        runBlocking { viewModel.attachments.addPrepared(0, picture("beach"), "") }
        val attachment = runBlocking { viewModel.await { it.attachments.flatten().isNotEmpty() } }.attachments.flatten()
            .single()
        val done = mutableListOf<String>()
        var open by mutableStateOf(true)
        compose.setContent {
            AlohaTheme {
                if (open) {
                    MediaEditor(attachment, null, viewModel.altText) { described, _, _ ->
                        done += described
                        open = false
                    }
                }
            }
        }
        compose.onNodeWithText("Draft a description").performClick()
        compose.onNodeWithText("Generated, please check").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        open = true
        compose.waitForIdle()
        compose.onNodeWithText("Draft a description").performClick()
        compose.onNodeWithText("Beach.").performTextReplacement("A beach at dusk.")
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("Beach. (AI generated)", "A beach at dusk."), done)
    }

    @Test
    fun `one picture is drafted from its card, the others left as they are`() = runBlocking {
        val viewModel = drafting()
        val beach = checkNotNull(viewModel.attachments.addPrepared(0, picture("beach"), ""))
        val reef = checkNotNull(viewModel.attachments.addPrepared(0, picture("reef"), ""))
        viewModel.await { it.draftsAltText }
        viewModel.altText.draftAll(only = beach)
        val drafted = viewModel.await { state -> state.attachments.flatten().any { it.description.isNotBlank() } }
            .attachments.flatten().associate { it.id to it.description }
        assertEquals("Beach. (AI generated)", drafted[beach])
        assertEquals("", drafted[reef])
        assertEquals(listOf(beach), viewModel.altText.unchecked())
    }

    @Test
    fun `a build that cannot read pictures offers no drafts`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.attachments.addPrepared(0, picture("beach"), "")
        val attachment = viewModel.await { it.attachments.flatten().isNotEmpty() }.attachments.flatten().single()
        assertFalse(viewModel.altText.offers(attachment))
    }
}
