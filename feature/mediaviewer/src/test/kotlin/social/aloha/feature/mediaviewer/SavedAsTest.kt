// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.mediaviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment

@RunWith(RobolectricTestRunner::class)
class SavedAsTest {
    private fun picture(url: String?) = MediaAttachment("m1", AttachmentKind.Image, url = url)

    @Test
    fun `a picture is saved under its id with its own extension`() {
        assertEquals("aloha-m1.jpg", savedAs(picture("https://cloud.example/media/abc.JPG"))?.second)
    }

    @Test
    fun `the server's file name never names a folder or another kind of file`() {
        val sneaky = picture("https://cloud.example/media/..%2F..%2FDownload%2Fupdate.apk")
        assertEquals("aloha-m1", savedAs(sneaky)?.second)
    }

    @Test
    fun `an address that is not the web is never downloaded`() {
        assertNull(savedAs(picture("content://other.app/secret.jpg")))
        assertNull(savedAs(picture(null)))
    }
}
