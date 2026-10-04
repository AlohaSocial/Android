// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.data.DeviceStorage

@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private class Caches : DeviceStorage {
        var bytes = 42_000L
        var cleared = 0

        override suspend fun cacheBytes() = bytes

        override suspend fun clear() {
            cleared++
            bytes = 0
        }
    }

    @After
    fun close() = Dispatchers.resetMain()

    @Test
    fun `the caches are measured, and cleared on asking`() {
        val caches = Caches()
        val storage = StorageViewModel(caches)
        assertEquals(StorageState(42_000L), storage.state.value)
        storage.clear()
        assertEquals(StorageState(0L, cleared = true), storage.state.value)
        assertEquals(1, caches.cleared)
    }
}
