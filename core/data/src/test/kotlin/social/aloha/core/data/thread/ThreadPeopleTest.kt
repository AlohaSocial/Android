// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.thread

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.database.CacheDatabase
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class ThreadPeopleTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        CacheDatabase::class.java,
    ).build()
    private val threads = ThreadRepository(StatusRepository(database.statusDao(), Clock.systemUTC()), fixture.clients)

    @After
    fun close() {
        database.close()
        fixture.close()
        server.close()
    }

    private fun people(vararg ids: String) = MockResponse.Builder().code(200)
        .body(ids.joinToString(",", "[", "]") { """{"id":"$it","username":"u$it","acct":"u$it"}""" })
        .addHeader("content-type", "application/json").build()

    @Test
    fun `a post opened again shows who favourited it at once, then the server's answer, and keeps it on a failure`() =
        runBlocking {
            val reader = fixture.signIn(server.url("/"))
            server.enqueue(people("1", "2"))
            server.enqueue(people("1", "2", "3"))
            server.enqueue(MockResponse.Builder().code(500).build())
            val first = threads.people(reader, "p", boosts = false, limit = 4).toList()
            assertEquals(listOf(listOf("1", "2")), first.map { list -> list.map { it.id } })
            val again = threads.people(reader, "p", boosts = false, limit = 4).toList()
            assertEquals(listOf(listOf("1", "2"), listOf("1", "2", "3")), again.map { list -> list.map { it.id } })
            val failed = threads.people(reader, "p", boosts = false, limit = 4).toList()
            assertEquals(listOf(listOf("1", "2", "3")), failed.map { list -> list.map { it.id } })
            // who boosted it is another list, never mistaken for who favourited it
            server.enqueue(people("9"))
            assertEquals(
                listOf(listOf("9")),
                threads.people(reader, "p", boosts = true, limit = 4).toList().map { l ->
                    l.map { it.id }
                },
            )
        }
}
