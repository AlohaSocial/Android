// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedContentTest {
    private val own = "social.aloha"

    @Test
    fun `a page shared from a browser comes as its title and address, once`() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Surf report")
            .putExtra(Intent.EXTRA_TEXT, "https://surf.example/today")
        assertEquals("Surf report\n\nhttps://surf.example/today", SharedContent.from(intent, own)?.text)
        val titled = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Surf report")
            .putExtra(Intent.EXTRA_TEXT, "Surf report https://surf.example/today")
        assertEquals("Surf report https://surf.example/today", SharedContent.from(titled, own)?.text)
    }

    @Test
    fun `only other apps' content is taken, never a file or the app's own`() {
        val theirs = Uri.parse("content://com.example.gallery/photo/1")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            arrayListOf(
                theirs,
                Uri.parse("file:///data/data/social.aloha/databases/accounts.db"),
                Uri.parse("content://social.aloha.composer.captures/captures/a.jpg"),
            ),
        )
        assertEquals(listOf(theirs), SharedContent.from(intent, own)?.media)
    }

    @Test
    fun `nothing to post is no share`() {
        assertNull(SharedContent.from(Intent(Intent.ACTION_SEND).setType("text/plain"), own))
        assertNull(SharedContent.from(Intent(Intent.ACTION_VIEW), own))
    }
}
