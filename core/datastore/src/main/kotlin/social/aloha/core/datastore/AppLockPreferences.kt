// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether the app asks for the reader's fingerprint, face or screen lock, and after how long away. */
public class AppLockPreferences(private val store: DataStore<Preferences>) {
    public val enabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: false }

    public suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    /** How long the app may be out of sight before it asks again, in seconds; 0 asks every time. */
    public val timeoutSeconds: Flow<Int> = store.data.map { it[TIMEOUT] ?: 0 }

    public suspend fun setTimeoutSeconds(seconds: Int) {
        store.edit { it[TIMEOUT] = seconds }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("app_lock")
        val TIMEOUT = intPreferencesKey("app_lock_timeout_seconds")
    }
}
