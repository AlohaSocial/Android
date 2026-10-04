// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.model.Digest
import social.aloha.core.model.QuietHours

/** When the device raises notifications: quiet hours and the digest, kept beside the app's other preferences. */
public class NotificationPreferences(private val store: DataStore<Preferences>) {
    /** The daily window in which no notification is raised; none until chosen. */
    public val quietHours: Flow<QuietHours?> = store.data.map { stored ->
        val from = stored[QUIET_FROM]
        val until = stored[QUIET_UNTIL]
        if (from != null && until != null) QuietHours(from, until) else null
    }

    public suspend fun setQuietHours(hours: QuietHours?) {
        store.edit {
            if (hours == null) {
                it.remove(QUIET_FROM)
                it.remove(QUIET_UNTIL)
            } else {
                it[QUIET_FROM] = hours.fromHour
                it[QUIET_UNTIL] = hours.untilHour
            }
        }
    }

    /** Notifications held for a summary at chosen hours; null while each is raised as it arrives. */
    public val digest: Flow<Digest?> = store.data.map { stored ->
        stored[DIGEST_HOURS]?.mapNotNull { it.toIntOrNull() }?.sorted()?.let { hours ->
            Digest(hours, personalNow = stored[DIGEST_PERSONAL_NOW] ?: true)
        }
    }

    public suspend fun setDigest(digest: Digest?) {
        store.edit {
            if (digest == null) {
                it.remove(DIGEST_HOURS)
                it.remove(DIGEST_PERSONAL_NOW)
            } else {
                it[DIGEST_HOURS] = digest.hours.map(Int::toString).toSet()
                it[DIGEST_PERSONAL_NOW] = digest.personalNow
            }
        }
    }

    private companion object {
        val QUIET_FROM = intPreferencesKey("quiet_from_hour")
        val QUIET_UNTIL = intPreferencesKey("quiet_until_hour")
        val DIGEST_HOURS = stringSetPreferencesKey("digest_hours")
        val DIGEST_PERSONAL_NOW = booleanPreferencesKey("digest_personal_now")
    }
}
