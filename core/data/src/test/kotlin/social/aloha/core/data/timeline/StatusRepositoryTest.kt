// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.database.CacheDatabase
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.ContentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.Status
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
class StatusRepositoryTest {
    private val database =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CacheDatabase::class.java).build()
    private val statuses = StatusRepository(database.statusDao(), Clock.systemUTC())

    @After
    fun close() {
        database.close()
    }

    @Test
    fun `saving many at once stores each and rewrites the boosts already stored`() = runBlocking {
        statuses.save("a", StatusSamples.boost)
        val favourited = StatusSamples.post().copy(favourited = true)
        statuses.saveAll("a", listOf(favourited, StatusSamples.reply))
        assertTrue(statuses.get("a", StatusSamples.boost.id)!!.reblog!!.favourited)
        assertEquals(StatusSamples.reply.id, statuses.get("a", StatusSamples.reply.id)?.id)
    }

    @Test
    fun `what a player saw settles a clip the server did not describe, for it and its boosts, for good`() =
        runBlocking {
            val clip = StatusSamples.post().copy(
                id = "30",
                mediaAttachments = listOf(MediaAttachment("v", AttachmentKind.Video)),
            )
            val boost = Status("31", StatusSamples.bob, createdAt = StatusSamples.NOW, reblog = clip)
            statuses.saveAll("a", listOf(clip, boost))
            assertEquals(ContentKind.Undetermined, statuses.kinds("a", listOf(clip)).getValue("30"))
            val seen = MediaDimensions(width = 1080, height = 1920, duration = 20.0)
            assertEquals(ContentKind.Short, statuses.reclassify("a", "30", "v", seen))
            assertEquals(
                mapOf("30" to ContentKind.Short, "31" to ContentKind.Short),
                statuses.kinds("a", listOf(clip, boost)),
            )
            // the server sends the clip again, still undescribed: what was settled stays
            statuses.save("a", clip)
            assertEquals(ContentKind.Short, statuses.kinds("a", listOf(clip)).getValue("30"))
        }
}
