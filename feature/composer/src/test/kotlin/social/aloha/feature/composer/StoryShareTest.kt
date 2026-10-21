// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.network.ApiError
import social.aloha.core.network.Refusal

class StoryShareTest {
    private val picture = Attachment("a", file = null, fileName = "beach.jpg", mimeType = "image/jpeg")
    private val document = Attachment("b", file = null, fileName = "notes.pdf", mimeType = "application/pdf")

    private fun fits(
        segments: List<String> = listOf("Sunset"),
        media: List<Attachment> = listOf(picture),
        alone: Boolean = true,
        card: Boolean = false,
    ) = StoryShare.fits(segments, media, alone, card)

    @Test
    fun `one picture with a short caption, or the words alone as a card, is a story`() {
        assertTrue(fits())
        assertTrue(fits(media = emptyList(), card = true))
    }

    @Test
    fun `a card story holds nothing but its words, and a picture story is no card`() {
        assertFalse(fits(card = true))
        assertFalse(fits(media = emptyList()))
    }

    @Test
    fun `a file, two pictures, a thread, a post not alone or a long caption is no story`() {
        assertFalse(fits(media = listOf(document)))
        assertFalse(fits(media = listOf(picture, picture)))
        assertFalse(fits(segments = listOf("One", "Two")))
        assertFalse(fits(alone = false))
        assertFalse(fits(segments = listOf("x".repeat(501))))
    }

    @Test
    fun `a reply, an edit or a post written again is not a story's to be`() {
        assertTrue(ComposerUiState().fresh)
        assertFalse(ComposerUiState(editing = true).fresh)
        assertFalse(ComposerUiState(replaces = "9").fresh)
    }

    @Test
    fun `stories at the server's limit say the limit, and any other refusal keeps the server's words`() {
        val full = ApiError.Unprocessable("already has 3 live shorts", Refusal("shorts-full", mapOf("limit" to "3")))
        assertEquals(PostFailure.StoriesFull(3), failureOf(full))
        assertEquals(PostFailure.Refused("too long"), failureOf(ApiError.Unprocessable("too long")))
        val noLimit = ApiError.Unprocessable("full", Refusal("shorts-full"))
        assertEquals(PostFailure.Refused("full"), failureOf(noLimit))
        val other = ApiError.Unprocessable("no", Refusal("something-new", mapOf("limit" to "3")))
        assertEquals(PostFailure.Refused("no"), failureOf(other))
    }
}
