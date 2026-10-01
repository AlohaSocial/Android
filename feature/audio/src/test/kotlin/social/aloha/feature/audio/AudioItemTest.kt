// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.testing.StatusSamples

class AudioItemTest {
    private val sound = MediaAttachment("a1", AttachmentKind.Audio, url = "https://cloud.example/m/a1.mp3")

    @Test
    fun `a sound is named by the first line of its post and by who posted it`() {
        val post = StatusSamples.post("<p>Night swim</p><p>recorded at the reef</p>")
            .copy(mediaAttachments = listOf(StatusSamples.image("i1"), sound))
        assertEquals(
            AudioItem("10", "https://cloud.example/m/a1.mp3", "Night swim", "Alice Example", null),
            audioItemOf(post),
        )
    }

    @Test
    fun `without words a sound is named by its description, else by its poster, and pictured by its preview`() {
        val described = sound.copy(description = "Waves", previewUrl = "https://cloud.example/m/a1.jpg")
        val item = audioItemOf(StatusSamples.post("").copy(mediaAttachments = listOf(described)))
        assertEquals("Waves", item?.title)
        assertEquals("https://cloud.example/m/a1.jpg", item?.artwork)
        assertEquals("Alice Example", audioItemOf(StatusSamples.post("").copy(mediaAttachments = listOf(sound)))?.title)
    }

    @Test
    fun `a boost plays the boosted post, and a post without a playable sound is no item`() {
        val boosted = StatusSamples.boost.copy(reblog = StatusSamples.post().copy(mediaAttachments = listOf(sound)))
        assertEquals("10", audioItemOf(boosted)?.statusId)
        assertNull(audioItemOf(StatusSamples.post()))
        assertNull(audioItemOf(StatusSamples.post().copy(mediaAttachments = listOf(sound.copy(url = null)))))
    }
}
