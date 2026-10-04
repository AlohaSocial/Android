// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingPreferencesTest {
    private class Memory(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `timelines open where they were left and hold new posts until told otherwise`() = runTest {
        val reading = ReadingPreferences(Memory(emptyPreferences()))
        assertTrue(reading.restorePosition.first())
        assertTrue(reading.newPostsPill.first())
        reading.setRestorePosition(false)
        reading.setNewPostsPill(false)
        assertFalse(reading.restorePosition.first())
        assertFalse(reading.newPostsPill.first())
    }
}
