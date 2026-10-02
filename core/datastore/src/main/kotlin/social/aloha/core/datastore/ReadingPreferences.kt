// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the reader reads the device's timelines, kept beside the app's other preferences. */
public class ReadingPreferences(private val store: DataStore<Preferences>) {
    /** Whether a timeline opens where it was left, as it does until the reader says otherwise. */
    public val restorePosition: Flow<Boolean> = store.data.map { it[RESTORE_POSITION] ?: true }

    public suspend fun setRestorePosition(restore: Boolean) {
        store.edit { it[RESTORE_POSITION] = restore }
    }

    /** Whether new posts wait behind a pill; off, they join the timeline as they arrive. */
    public val newPostsPill: Flow<Boolean> = store.data.map { it[NEW_POSTS_PILL] ?: true }

    public suspend fun setNewPostsPill(pill: Boolean) {
        store.edit { it[NEW_POSTS_PILL] = pill }
    }

    private companion object {
        val RESTORE_POSITION = booleanPreferencesKey("restore_position")
        val NEW_POSTS_PILL = booleanPreferencesKey("new_posts_pill")
    }
}
