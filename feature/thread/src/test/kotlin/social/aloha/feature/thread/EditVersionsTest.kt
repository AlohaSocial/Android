// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.StatusEdit
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors

class EditVersionsTest {
    @Test
    fun `each version says what its pictures say, so an edit of a description alone shows`() {
        fun edit(vararg descriptions: String?) = StatusEdit(
            StatusSamples.post().account,
            "<p>Aloha</p>",
            mediaAttachments = descriptions.mapIndexed { i, text ->
                MediaAttachment("$i", AttachmentKind.Image, "https://x/$i.png", description = text)
            },
        )
        val versions = ThreadPresentation.versions(listOf(edit("Before", " "), edit("After", null)), COLORS)
        assertEquals(listOf("Before", null), versions[0].media)
        assertEquals(listOf("After", null), versions[1].media)
    }

    private companion object {
        val COLORS = RichTextColors(Color.Black, Color.Black, Color.Black)
    }
}
