// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheDaoTest {
    private lateinit var database: CacheDatabase

    @Before
    fun open() {
        database =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CacheDatabase::class.java).build()
    }

    @After
    fun close() {
        database.close()
    }

    private fun status(
        id: String,
        author: String = "alice",
        boosted: String? = null,
        cachedAt: Long = 1_000,
        account: String = "a",
    ) = CachedStatusEntity(
        account, id, """{"id":"$id"}""", cachedAt, "text", "post $id", author, boosted,
        reblogOfId = boosted?.let {
            "inner-$id"
        },
    )

    private fun entry(
        id: String,
        position: Long,
        key: String = "home:home",
        gap: Boolean = false,
        account: String = "a",
    ) = TimelineEntryEntity(account, key, id, position, gap, insertedAt = 0)

    @Test
    fun `a timeline reads in position order with its statuses, gaps without one`() = runBlocking {
        val dao = database.timelineDao()
        dao.apply(
            "a",
            "home:home",
            listOf(status("1"), status("2")),
            listOf(entry("1", 10), entry("gap:1", 9, gap = true), entry("2", 8)),
        )
        val rows = dao.observeRows("a", "home:home", limit = 10).first()
        assertEquals(listOf("1", "gap:1", "2"), rows.map { it.statusId })
        assertNull(rows[1].payloadJson)
        assertTrue(rows[1].isGap)
    }

    @Test
    fun `a status stored once is updated in every timeline that shows it`() = runBlocking {
        val timelines = database.timelineDao()
        timelines.apply("a", "home:home", listOf(status("1")), listOf(entry("1", 10)))
        timelines.apply("a", "public:local", emptyList(), listOf(entry("1", 5, key = "public:local")))
        database.statusDao().upsert(status("1").copy(payloadJson = """{"id":"1","favourited":true}"""))
        for (key in listOf("home:home", "public:local")) {
            assertTrue(timelines.observeRows("a", key, 10).first().single().payloadJson!!.contains("favourited"))
        }
    }

    @Test
    fun `applying a page replaces the timeline's order and keeps other timelines`() = runBlocking {
        val dao = database.timelineDao()
        dao.apply("a", "home:home", listOf(status("1"), status("2")), listOf(entry("1", 10), entry("2", 9)))
        dao.apply("a", "public:local", emptyList(), listOf(entry("2", 3, key = "public:local")))
        dao.apply("a", "home:home", listOf(status("3")), listOf(entry("3", 11), entry("1", 10)))
        assertEquals(listOf("3", "1"), dao.entries("a", "home:home").map { it.statusId })
        assertEquals(listOf("2"), dao.entries("a", "public:local").map { it.statusId })
    }

    @Test
    fun `a sweep removes old statuses nothing points at, and no more than it may`() = runBlocking {
        database.timelineDao().apply("a", "home:home", listOf(status("kept", cachedAt = 0)), listOf(entry("kept", 1)))
        val statuses = database.statusDao()
        (1..5).forEach { statuses.upsert(status("old$it", cachedAt = 0)) }
        statuses.upsert(status("recent", cachedAt = 5_000))

        assertEquals(3, statuses.deleteOrphans(cutoff = 1_000, limit = 3))
        assertEquals(2, statuses.deleteOrphans(cutoff = 1_000, limit = 3))
        assertEquals(0, statuses.deleteOrphans(cutoff = 1_000, limit = 3))
        assertEquals("kept", statuses.get("a", "kept")?.serverId)
        assertEquals("recent", statuses.get("a", "recent")?.serverId)
    }

    @Test
    fun `blocking an author removes what they posted and what they were boosted in, everywhere`() = runBlocking {
        val statuses =
            listOf(status("own", author = "bob"), status("boost", author = "alice", boosted = "bob"), status("other"))
        database.timelineDao().apply(
            "a",
            "home:home",
            statuses,
            listOf(entry("own", 3), entry("boost", 2), entry("other", 1)),
        )
        database.statusDao().deleteByAuthor("a", "bob")
        assertEquals(listOf("other"), database.timelineDao().entries("a", "home:home").map { it.statusId })
        assertNull(database.statusDao().get("a", "own"))
    }

    @Test
    fun `the boosts of a status are found by what they boost`() = runBlocking {
        val statuses = database.statusDao()
        statuses.upsertAll(
            listOf(
                status("inner"),
                status("b1", boosted = "alice").copy(reblogOfId = "inner"),
                status("b2", boosted = "alice").copy(reblogOfId = "other"),
            ),
        )
        assertEquals(listOf("b1"), statuses.boostsOf("a", "inner").map { it.serverId })
    }

    @Test
    fun `deleting a status takes its boosts with it, from every timeline`() = runBlocking {
        val statuses = listOf(
            status("inner"),
            status("b1", boosted = "alice").copy(reblogOfId = "inner"),
            status("b2", boosted = "alice").copy(reblogOfId = "other"),
        )
        database.timelineDao().apply(
            "a",
            "home:home",
            statuses,
            listOf(entry("inner", 3), entry("b1", 2), entry("b2", 1)),
        )
        database.statusDao().delete("a", "inner")
        assertEquals(listOf("b2"), database.timelineDao().entries("a", "home:home").map { it.statusId })
        assertNull(database.statusDao().get("a", "b1"))
    }

    @Test
    fun `a timeline position is kept per account and timeline`() = runBlocking {
        val positions = database.positionDao()
        positions.set(TimelinePositionEntity("a", "home:home", "42", 17))
        positions.set(TimelinePositionEntity("a", "home:home", "43", 0))
        assertEquals(TimelinePositionEntity("a", "home:home", "43", 0), positions.get("a", "home:home"))
        assertNull(positions.get("b", "home:home"))
    }

    @Test
    fun `removing an account takes only that account's cache`() = runBlocking {
        database.timelineDao().apply("a", "home:home", listOf(status("1")), listOf(entry("1", 1)))
        database.timelineDao().apply(
            "b",
            "home:home",
            listOf(status("1", account = "b")),
            listOf(entry("1", 1, account = "b")),
        )
        database.filterDao().replace("a", listOf(FilterEntity("a", "f", "{}", null)))
        database.cacheAccountDao().deleteEverything("a")
        assertTrue(database.timelineDao().entries("a", "home:home").isEmpty())
        assertTrue(database.filterDao().observe("a").first().isEmpty())
        assertEquals(1, database.timelineDao().entries("b", "home:home").size)
    }
}
