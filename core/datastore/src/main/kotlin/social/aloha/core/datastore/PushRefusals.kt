// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The accounts whose server refused the distributor's endpoint, so they push through their Nextcloud instead. */
public class PushRefusals(private val store: DataStore<Preferences>) {
    public val accounts: Flow<Set<String>> = store.data.map { it[REFUSED].orEmpty() }

    public suspend fun set(accountId: String, refused: Boolean) {
        store.edit {
            val now = it[REFUSED].orEmpty()
            it[REFUSED] = if (refused) now + accountId else now - accountId
        }
    }

    private companion object {
        val REFUSED = stringSetPreferencesKey("push_refused")
    }
}
