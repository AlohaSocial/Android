// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModePreferencesTest {
    private class Memory(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `the modes are offered once`() = runTest {
        val preferences = ModePreferences(Memory(emptyPreferences()))
        assertFalse(preferences.modesOffered.first())
        preferences.modesWereOffered()
        assertTrue(preferences.modesOffered.first())
    }

    @Test
    fun `someone who chose modes before the offer existed is not offered them`() = runTest {
        val stored = mutablePreferencesOf(stringSetPreferencesKey("optional_modes") to emptySet())
        assertTrue(ModePreferences(Memory(stored)).modesOffered.first())
    }

    @Test
    fun `shorts loop and play on mobile data until the reader says otherwise`() = runTest {
        val preferences = ModePreferences(Memory(emptyPreferences()))
        assertTrue(preferences.loopShorts.first())
        assertTrue(preferences.autoplayOnMobileData.first())
        preferences.setLoopShorts(false)
        preferences.setAutoplayOnMobileData(false)
        assertFalse(preferences.loopShorts.first())
        assertFalse(preferences.autoplayOnMobileData.first())
    }
}
