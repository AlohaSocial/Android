// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEditTest {
    private val video = VideoInfo(durationMs = 60_000, width = 1920, height = 1080)

    @Test
    fun `the whole video at its own size is as large as it was`() {
        assertEquals(100L, VideoEdit.whole(video).estimate(100, video))
    }

    @Test
    fun `keeping half keeps about half`() {
        assertEquals(50L, VideoEdit(0, 30_000).estimate(100, video))
    }

    @Test
    fun `a smaller size shrinks by the share of pixels kept`() {
        // 720 of 1080 on the shorter side keeps four ninths of the pixels
        assertEquals(44L, VideoEdit(0, 60_000, shortSide = 720).estimate(100, video))
    }

    @Test
    fun `a size no smaller than the video changes nothing`() {
        assertEquals(100L, VideoEdit(0, 60_000, shortSide = 1080).estimate(100, video))
    }

    @Test
    fun `an upright video's shorter side is its width`() {
        assertEquals(1080, VideoInfo(10_000, 1080, 1920).shortSide)
    }
}
