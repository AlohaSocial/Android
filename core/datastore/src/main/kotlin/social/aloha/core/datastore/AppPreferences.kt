// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-wide settings that are not secrets: which account is active, which terms were accepted. */
public class AppPreferences(private val store: DataStore<Preferences>) {
    public val activeAccountId: Flow<String?> = store.data.map { it[ACTIVE_ACCOUNT] }

    /** The version of the terms the person accepted; 0 before they accepted any. */
    public val acceptedTermsVersion: Flow<Int> = store.data.map { it[ACCEPTED_TERMS] ?: 0 }

    public suspend fun setActiveAccountId(id: String?) {
        store.edit { if (id == null) it.remove(ACTIVE_ACCOUNT) else it[ACTIVE_ACCOUNT] = id }
    }

    public suspend fun acceptTerms(version: Int) {
        store.edit { it[ACCEPTED_TERMS] = version }
    }

    private companion object {
        val ACTIVE_ACCOUNT = stringPreferencesKey("active_account_id")
        val ACCEPTED_TERMS = intPreferencesKey("accepted_terms_version")
    }
}
