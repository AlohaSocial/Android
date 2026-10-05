// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What focus mode changed, as it was before: the numbers, the boosts folded, the trends, and a digest. */
public data class FocusBefore(val counts: Boolean, val carousel: Boolean, val trends: Boolean, val digest: Boolean)

/** Whether this device is in focus mode, and what it is to go back to once it is not. */
public class FocusPreferences(private val store: DataStore<Preferences>) {
    /** What to go back to; null while focus mode is off. */
    public val before: Flow<FocusBefore?> = store.data.map {
        if (it[ON] != true) {
            null
        } else {
            FocusBefore(it[COUNTS] ?: true, it[CAROUSEL] ?: false, it[TRENDS] ?: true, it[DIGEST] ?: false)
        }
    }

    public suspend fun remember(before: FocusBefore?) {
        store.edit {
            it[ON] = before != null
            if (before != null) {
                it[COUNTS] = before.counts
                it[CAROUSEL] = before.carousel
                it[TRENDS] = before.trends
                it[DIGEST] = before.digest
            }
        }
    }

    private companion object {
        val ON = booleanPreferencesKey("focus_on")
        val COUNTS = booleanPreferencesKey("focus_before_counts")
        val CAROUSEL = booleanPreferencesKey("focus_before_carousel")
        val TRENDS = booleanPreferencesKey("focus_before_trends")
        val DIGEST = booleanPreferencesKey("focus_before_digest")
    }
}
