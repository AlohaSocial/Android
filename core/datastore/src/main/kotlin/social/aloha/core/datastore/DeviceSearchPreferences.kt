// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether the phone's own search finds what the app gives it; on until turned off. */
public class DeviceSearchPreferences(private val store: DataStore<Preferences>) {
    public val shown: Flow<Boolean> = store.data.map { it[SHOWN] ?: true }

    public suspend fun setShown(shown: Boolean) {
        store.edit { it[SHOWN] = shown }
    }

    private companion object {
        val SHOWN = booleanPreferencesKey("device_search")
    }
}
