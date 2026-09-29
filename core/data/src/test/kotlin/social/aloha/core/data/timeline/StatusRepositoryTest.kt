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
}
