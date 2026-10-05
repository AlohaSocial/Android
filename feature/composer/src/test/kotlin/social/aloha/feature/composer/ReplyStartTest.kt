// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.model.ReplyPrefix
import social.aloha.core.model.Visibility
import social.aloha.core.model.Writing
import social.aloha.core.testing.StatusSamples

class ReplyStartTest {
    private fun warned(text: String) = StatusSamples.post().copy(spoilerText = text)

    @Test
    fun `a reply takes over the warning, after re where the writer wants it, and never twice`() {
        val author = StatusSamples.post().account.id
        assertEquals("re: Spoilers", inheritedWarning(warned(" Spoilers "), ReplyPrefix.Always, own = author))
        assertEquals("Re: Spoilers", inheritedWarning(warned("Re: Spoilers"), ReplyPrefix.Always, own = "9"))
        assertEquals("Spoilers", inheritedWarning(warned("Spoilers"), ReplyPrefix.ToOthers, own = author))
        assertEquals("re: Spoilers", inheritedWarning(warned("Spoilers"), ReplyPrefix.ToOthers, own = "9"))
        assertEquals("Spoilers", inheritedWarning(warned("Spoilers"), ReplyPrefix.Never, own = "9"))
        assertEquals("", inheritedWarning(warned("  "), ReplyPrefix.Always, own = "9"))
    }

    @Test
    fun `a reply to a public post starts quiet public, in the language of the post`() {
        val public = StatusSamples.post().copy(visibility = Visibility.Public, language = "haw")
        val answered = ComposerUiState(visibility = Visibility.Public, language = "en").answering(public, Writing())
        assertEquals(Visibility.Unlisted, answered.visibility)
        assertEquals("haw", answered.language)
        val loud = ComposerUiState(visibility = Visibility.Public).answering(public, Writing(quietReplies = false))
        assertEquals(Visibility.Public, loud.visibility)
        // a narrower choice already made stays
        val private = ComposerUiState(visibility = Visibility.Private).answering(public, Writing())
        assertEquals(Visibility.Private, private.visibility)
    }
}
