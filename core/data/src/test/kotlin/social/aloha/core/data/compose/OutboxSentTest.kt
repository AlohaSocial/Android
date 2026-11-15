// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.database.OutboxDao
import social.aloha.core.database.OutboxDatabase
import social.aloha.core.database.OutboxEntity
import social.aloha.core.model.OutboxState

@RunWith(RobolectricTestRunner::class)
class OutboxSentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, OutboxDatabase::class.java).build()
    private val sent = File(context.filesDir, "sent.jpg").apply { writeBytes(ByteArray(8)) }
    private val kept = File(context.filesDir, "kept.jpg").apply { writeBytes(ByteArray(8)) }

    /** The writes made, each with whether the file of the segment sent was still there when it was made. */
    private val writes = CopyOnWriteArrayList<Pair<String, Boolean>>()
    private val dao = database.outboxDao().let { real ->
        object : OutboxDao by real {
            override suspend fun setState(id: String, state: String, error: String?, now: Long) {
                writes += "state" to sent.exists()
                real.setState(id, state, error, now)
            }

            override suspend fun setContent(id: String, content: String, now: Long) {
                writes += "content" to sent.exists()
                real.setContent(id, content, now)
            }

            override suspend fun upsert(entry: OutboxEntity) {
                writes += "row" to sent.exists()
                real.upsert(entry)
            }

            override suspend fun delete(id: String) {
                writes += "delete" to sent.exists()
                real.delete(id)
            }
        }
    }
    private val outbox = Outbox(dao, Clock.systemUTC())

    private fun media(file: File) = DraftMedia(file.name, "image/jpeg", file.absolutePath, mediaId = file.name)

    @After
    fun close() {
        database.close()
        sent.delete()
        kept.delete()
    }

    @Test
    fun `a rest is kept as a draft in one write, and the files sent go only after it`() = runBlocking {
        val thread =
            DraftPost(listOf(DraftSegment("one", listOf(media(sent))), DraftSegment("two", listOf(media(kept)))))
        outbox.queue("p", "a", thread)
        writes.clear()
        val rest = DraftPost(listOf(DraftSegment("two", listOf(media(kept)))))
        outbox.sent("p", rest)
        assertEquals(listOf("row" to true), writes)
        val draft = checkNotNull(outbox.get("p"))
        assertEquals(OutboxState.Draft, draft.state)
        assertEquals(rest, draft.post)
        assertFalse(sent.exists())
        assertTrue(kept.exists())
    }

    @Test
    fun `a post out is deleted before its files`() = runBlocking {
        outbox.queue("p", "a", DraftPost(listOf(DraftSegment("one", listOf(media(sent))))))
        writes.clear()
        outbox.sent("p")
        assertEquals(listOf("delete" to true), writes)
        assertNull(outbox.get("p"))
        assertFalse(sent.exists())
    }
}
