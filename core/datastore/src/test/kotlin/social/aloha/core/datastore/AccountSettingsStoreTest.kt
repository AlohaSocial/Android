// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import social.aloha.core.model.TimelineSource

class AccountSettingsStoreTest {
    private class Memory<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state

        override suspend fun updateData(transform: suspend (t: T) -> T): T = transform(state.value).also {
            state.value =
                it
        }
    }

    private val store = AccountSettingsStore(Memory(emptyMap()))

    @Test
    fun `an account with nothing stored reads the defaults`() = runTest {
        assertEquals(AccountSettings(), store.settings("a").first())
    }

    @Test
    fun `settings are kept per account, and forgetting one leaves the others`() = runTest {
        store.update("a") { it.copy(showBoosts = false, homeSource = TimelineSource.Local) }
        store.update("b") { it.copy(showReplies = false) }
        assertEquals(
            AccountSettings(showBoosts = false, homeSource = TimelineSource.Local),
            store.settings("a").first(),
        )
        store.forget("a")
        assertEquals(AccountSettings(), store.settings("a").first())
        assertFalse(store.settings("b").first().showReplies)
    }

    @Test
    fun `a file from another build reads what it can and keeps its own defaults`() = runTest {
        val written = """{"a":{"showBoosts":false,"somethingNewer":42}}"""
        val read = AccountSettingsSerializer.readFrom(ByteArrayInputStream(written.encodeToByteArray()))
        assertEquals(AccountSettings(showBoosts = false), read.getValue("a"))
    }

    @Test
    fun `what is written reads back the same`() = runTest {
        val settings = mapOf("a" to AccountSettings(showReplies = false, homeSource = TimelineSource.Federated))
        val bytes = ByteArrayOutputStream().also { AccountSettingsSerializer.writeTo(settings, it) }.toByteArray()
        assertEquals(settings, AccountSettingsSerializer.readFrom(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `an unreadable file is reported as corrupt, which the store answers by starting over`() = runTest {
        assertThrows(CorruptionException::class.java) {
            kotlinx.coroutines.runBlocking {
                AccountSettingsSerializer.readFrom(ByteArrayInputStream("not json".encodeToByteArray()))
            }
        }
    }
}
