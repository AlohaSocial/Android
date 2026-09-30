// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.model.ServerLimits

class UploadPreflightTest {
    private val limits = ServerLimits.MastodonDefaults.copy(
        imageSizeLimit = 10L * MB,
        videoSizeLimit = 40L * MB,
        supportedMimeTypes = listOf("image/jpeg", "image/png", "image/gif", "video/mp4"),
    )

    @Test
    fun `a file of a taken type under its ceiling fits`() {
        assertEquals(Preflight.Fits, UploadPreflight.check("image/jpeg", 2 * MB, limits))
        assertEquals(Preflight.Fits, UploadPreflight.check("video/mp4", 30 * MB, limits))
    }

    @Test
    fun `pictures and videos each have their own ceiling`() {
        assertEquals(Preflight.ShrinkPicture, UploadPreflight.check("image/jpeg", 12 * MB, limits))
        assertEquals(Preflight.TooLarge(40L * MB), UploadPreflight.check("video/mp4", 41 * MB, limits))
    }

    @Test
    fun `heic becomes jpeg where the server does not take it, other types are refused`() {
        assertEquals(Preflight.ShrinkPicture, UploadPreflight.check("image/heic", 1 * MB, limits))
        assertEquals(Preflight.Unsupported("video/x-matroska"), UploadPreflight.check("video/x-matroska", MB, limits))
    }

    @Test
    fun `a moving picture over the ceiling is refused, never flattened to its first frame`() {
        assertEquals(Preflight.TooLarge(10L * MB), UploadPreflight.check("image/gif", 11 * MB, limits))
    }

    @Test
    fun `a server that lists no types takes any`() {
        val open = limits.copy(supportedMimeTypes = emptyList())
        assertEquals(Preflight.Fits, UploadPreflight.check("application/pdf", MB, open))
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}
