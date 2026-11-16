// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.MediaMeta

class StillUrlTest {
    private fun picture(
        original: Int? = 2000,
        small: Int? = 800,
        url: String? = ORIGINAL,
        preview: String? = PREVIEW,
        type: AttachmentKind = AttachmentKind.Image,
    ) = MediaAttachment(
        id = "1",
        type = type,
        url = url,
        previewUrl = preview,
        meta = MediaMeta(original = MediaDimensions(width = original), small = MediaDimensions(width = small)),
    )

    @Test
    fun `a picture drawn wider than its preview loads the original`() {
        assertEquals(ORIGINAL, stillUrl(picture(), 1080))
    }

    @Test
    fun `a picture drawn no wider than its preview keeps the preview`() {
        assertEquals(PREVIEW, stillUrl(picture(), 800))
        assertEquals(PREVIEW, stillUrl(picture(), 0))
    }

    @Test
    fun `an unknown width on either side keeps the preview`() {
        assertEquals(PREVIEW, stillUrl(picture(original = null), 1080))
        assertEquals(PREVIEW, stillUrl(picture(small = null), 1080))
        assertEquals(PREVIEW, stillUrl(MediaAttachment("1", AttachmentKind.Image, ORIGINAL, PREVIEW), 1080))
    }

    @Test
    fun `an original no wider than the preview, or wider than 2560 pixels, keeps the preview`() {
        assertEquals(PREVIEW, stillUrl(picture(original = 800), 1080))
        assertEquals(PREVIEW, stillUrl(picture(original = 2561), 1080))
        assertEquals(ORIGINAL, stillUrl(picture(original = 2560), 1080))
    }

    @Test
    fun `a gif, a gifv and a video keep their still preview`() {
        assertEquals(PREVIEW, stillUrl(picture(url = "https://x.example/a.GIF?v=1"), 1080))
        assertEquals(PREVIEW, stillUrl(picture(type = AttachmentKind.Gifv), 1080))
        assertEquals(PREVIEW, stillUrl(picture(type = AttachmentKind.Video), 1080))
    }

    @Test
    fun `without a preview the original is drawn, and without an original the preview`() {
        assertEquals(ORIGINAL, stillUrl(picture(preview = null), 100))
        assertEquals(PREVIEW, stillUrl(picture(url = null), 1080))
    }
}

private const val ORIGINAL = "https://x.example/a.jpg"
private const val PREVIEW = "https://x.example/a-small.jpg"
