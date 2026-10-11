// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether this device shows every account together or one at a time; together until the reader picks one. */
public class AccountViewPreferences(private val store: DataStore<Preferences>) {
    public val allAccounts: Flow<Boolean> = store.data.map { it[ALL] ?: true }

    public suspend fun setAllAccounts(all: Boolean) {
        store.edit { it[ALL] = all }
    }

    private companion object {
        val ALL = booleanPreferencesKey("all_accounts")
    }
}
