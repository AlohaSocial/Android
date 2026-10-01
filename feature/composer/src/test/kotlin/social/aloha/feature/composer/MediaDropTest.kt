// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaDropTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gallery = Uri.parse("content://com.example.gallery/photo/1")

    private fun clip(vararg uris: Uri, type: String = "image/jpeg") =
        ClipData(ClipDescription("dragged", arrayOf(type)), ClipData.Item(uris.first())).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }

    @Test
    fun `pictures and videos another app dragged in attach, the app's own files never`() {
        val dropped = clip(
            gallery,
            Uri.parse("file:///data/data/${context.packageName}/databases/accounts.db"),
            Uri.parse("content://${context.packageName}.composer.captures/captures/a.jpg"),
        )
        assertEquals(listOf(gallery), mediaIn(dropped, context))
    }

    @Test
    fun `only a clip offering pictures or videos starts a drop`() {
        assertTrue(carriesMedia(ClipDescription("v", arrayOf("video/mp4"))))
        assertFalse(carriesMedia(ClipDescription("t", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))))
        assertEquals(emptyList<Uri>(), mediaIn(clip(gallery, type = ClipDescription.MIMETYPE_TEXT_PLAIN), context))
    }
}
