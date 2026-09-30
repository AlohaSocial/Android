// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.compose.DraftMedia
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.DraftSegment
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class OutboxTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val outbox = fixture.outbox
    private val uploads = File(context.filesDir, "uploads").apply { mkdirs() }

    @After
    fun close() = fixture.close()

    private fun file(name: String, age: Long = 0) = File(uploads, name).apply {
        writeBytes(ByteArray(8))
        setLastModified(System.currentTimeMillis() - age)
    }

    private fun post(file: File) =
        DraftPost(listOf(DraftSegment("With a picture", listOf(DraftMedia("a.jpg", "image/jpeg", file.absolutePath)))))

    @Test
    fun `a post going out cannot be deleted, and its files stay`() = runBlocking {
        val account = fixture.signIn("http://social.example/".toHttpUrl())
        val picture = file("a.jpg")
        outbox.queue("p", account.id, post(picture))
        outbox.claim(account.id)
        assertFalse(outbox.delete("p", files = true))
        assertTrue(picture.exists())
    }

    @Test
    fun `signing out forgets what was not sent, and its files`() = runBlocking {
        val account = fixture.signIn("http://social.example/".toHttpUrl())
        val picture = file("b.jpg")
        outbox.saveDraft("d", account.id, post(picture))
        outbox.forget(account.id)
        assertEquals(emptyList<Any>(), outbox.observe(account.id).first())
        assertFalse(picture.exists())
    }

    @Test
    fun `a sweep deletes old copies no post attaches, and keeps the rest`() = runBlocking {
        val account = fixture.signIn("http://social.example/".toHttpUrl())
        val day = 86_400_000L
        val attached = file("kept.jpg", age = 2 * day)
        val orphan = file("orphan.jpg", age = 2 * day)
        val fresh = file("fresh.jpg")
        outbox.saveDraft("d", account.id, post(attached))
        outbox.sweep(uploads)
        assertTrue(attached.exists())
        assertFalse(orphan.exists())
        assertTrue("a composer open now keeps its files", fresh.exists())
    }
}
