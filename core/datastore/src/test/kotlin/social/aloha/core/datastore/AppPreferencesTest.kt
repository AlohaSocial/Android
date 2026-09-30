// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import social.aloha.core.model.SwipeAction

class AppPreferencesTest {
    private class Memory(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `a swipe favourites towards the end and boosts towards the start until chosen otherwise`() = runTest {
        val preferences = AppPreferences(Memory(emptyPreferences()))
        assertEquals(SwipeAction.Favourite, preferences.swipeTowardsEnd.first())
        assertEquals(SwipeAction.Boost, preferences.swipeTowardsStart.first())
        preferences.setSwipeTowardsEnd(SwipeAction.Reply)
        preferences.setSwipeTowardsStart(SwipeAction.None)
        assertEquals(SwipeAction.Reply, preferences.swipeTowardsEnd.first())
        assertEquals(SwipeAction.None, preferences.swipeTowardsStart.first())
    }

    @Test
    fun `a choice a later build wrote, which this one does not know, reads as the default`() = runTest {
        val stored = mutablePreferencesOf(stringPreferencesKey("swipe_towards_end") to "Translate")
        assertEquals(SwipeAction.Favourite, AppPreferences(Memory(stored)).swipeTowardsEnd.first())
    }
}
