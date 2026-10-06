// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which on-device features the reader turned on; each is off until they do. */
public data class IntelligenceChoices(
    val altText: Boolean = false,
    val rewrite: Boolean = false,
    val summary: Boolean = false,
)

/** The on-device features' switches, one each and no master switch. */
public class IntelligencePreferences(private val store: DataStore<Preferences>) {
    public val choices: Flow<IntelligenceChoices> = store.data.map { it.choices() }

    public suspend fun update(change: (IntelligenceChoices) -> IntelligenceChoices) {
        store.edit {
            val now = change(it.choices())
            it[ALT_TEXT] = now.altText
            it[REWRITE] = now.rewrite
            it[SUMMARY] = now.summary
        }
    }

    private fun Preferences.choices() =
        IntelligenceChoices(this[ALT_TEXT] == true, this[REWRITE] == true, this[SUMMARY] == true)

    private companion object {
        val ALT_TEXT = booleanPreferencesKey("intelligence_alt_text")
        val REWRITE = booleanPreferencesKey("intelligence_rewrite")
        val SUMMARY = booleanPreferencesKey("intelligence_summary")
    }
}
