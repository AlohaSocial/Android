// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountDaoTest {
    private lateinit var database: AccountsDatabase
    private val dao get() = database.accountDao()

    @Before
    fun open() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AccountsDatabase::class.java,
            ).build()
    }

    @After
    fun close() {
        database.close()
    }

    private fun account(id: String, sortIndex: Int, needsReauth: Boolean = false) = AccountEntity(
        id = id,
        instanceHost = "cloud.example",
        apiBase = "https://cloud.example/index.php/apps/social/",
        serverAccountId = "server-$id",
        handle = "alice",
        displayName = "Alice",
        avatarUrl = null,
        headerUrl = null,
        capabilitiesJson = null,
        needsReauth = needsReauth,
        profilePending = false,
        sortIndex = sortIndex,
        addedAt = 0,
        nextcloudConnected = false,
    )

    @Test
    fun `accounts come back in sort order`() = runTest {
        dao.upsert(account("b", 1))
        dao.upsert(account("a", 0))
        assertEquals(listOf("a", "b"), dao.observeAll().first().map { it.id })
        assertEquals(2, dao.nextSortIndex())
    }

    @Test
    fun `marking every account for a new sign-in keeps the accounts`() = runTest {
        dao.upsert(account("a", 0))
        dao.upsert(account("b", 1))
        dao.markAllNeedReauth()
        assertTrue(dao.all().all { it.needsReauth })
        assertEquals(2, dao.all().size)
    }

    @Test
    fun `an account is found by host and server id, and deleted by id`() = runTest {
        dao.upsert(account("a", 0))
        assertEquals("a", dao.find("cloud.example", "server-a")?.id)
        dao.delete("a")
        assertNull(dao.get("a"))
    }

    @Test
    fun `a registration is stored per host`() = runTest {
        dao.upsertRegistration(ClientRegistrationEntity("cloud.example", "client-1", "read write follow push", 0))
        assertEquals("client-1", dao.registration("cloud.example")?.clientId)
        assertNull(dao.registration("other.example"))
    }
}
