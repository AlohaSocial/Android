// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.model.SwipeAction

/**
 * App-wide settings that are not secrets: which account is active, which terms were accepted, and how
 * the device behaves whichever account reads on it, such as what a swipe on a post does.
 */
public class AppPreferences(private val store: DataStore<Preferences>) {
    public val activeAccountId: Flow<String?> = store.data.map { it[ACTIVE_ACCOUNT] }

    /** The version of the terms the person accepted; 0 before they accepted any. */
    public val acceptedTermsVersion: Flow<Int> = store.data.map { it[ACCEPTED_TERMS] ?: 0 }

    /** A swipe towards the end of the line; favouriting until chosen otherwise. */
    public val swipeTowardsEnd: Flow<SwipeAction> = store.data.map { swipe(it[SWIPE_END], SwipeAction.Favourite) }

    /** A swipe towards the start of the line; boosting until chosen otherwise. */
    public val swipeTowardsStart: Flow<SwipeAction> = store.data.map { swipe(it[SWIPE_START], SwipeAction.Boost) }

    /** Whether posting warns when a picture has no description; on until turned off. It never blocks. */
    public val warnMissingDescription: Flow<Boolean> = store.data.map { it[WARN_DESCRIPTION] ?: true }

    /** Whether a short recorded in the composer gets `#shorts`; null until the person was asked once. */
    public val tagShorts: Flow<Boolean?> = store.data.map { it[TAG_SHORTS] }

    /**
     * Whether timelines wait for Wi-Fi before they refresh on their own; off until turned on. The unread
     * count and notifications are asked for on any network, since a wrong badge costs more than the bytes.
     */
    public val wifiOnlySync: Flow<Boolean> = store.data.map { it[WIFI_ONLY_SYNC] ?: false }

    public suspend fun setWifiOnlySync(wifiOnly: Boolean) {
        store.edit { it[WIFI_ONLY_SYNC] = wifiOnly }
    }

    public suspend fun setTagShorts(tag: Boolean) {
        store.edit { it[TAG_SHORTS] = tag }
    }

    public suspend fun setWarnMissingDescription(warn: Boolean) {
        store.edit { it[WARN_DESCRIPTION] = warn }
    }

    public suspend fun setSwipeTowardsEnd(action: SwipeAction) {
        store.edit { it[SWIPE_END] = action.name }
    }

    public suspend fun setSwipeTowardsStart(action: SwipeAction) {
        store.edit { it[SWIPE_START] = action.name }
    }

    public suspend fun setActiveAccountId(id: String?) {
        store.edit { if (id == null) it.remove(ACTIVE_ACCOUNT) else it[ACTIVE_ACCOUNT] = id }
    }

    public suspend fun acceptTerms(version: Int) {
        store.edit { it[ACCEPTED_TERMS] = version }
    }

    private companion object {
        val SWIPE_END = stringPreferencesKey("swipe_towards_end")
        val SWIPE_START = stringPreferencesKey("swipe_towards_start")
        val ACTIVE_ACCOUNT = stringPreferencesKey("active_account_id")
        val WARN_DESCRIPTION = booleanPreferencesKey("warn_missing_description")
        val TAG_SHORTS = booleanPreferencesKey("tag_shorts")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")

        /** A value a later build wrote, or none at all, reads as the default. */
        fun swipe(stored: String?, default: SwipeAction): SwipeAction =
            SwipeAction.entries.firstOrNull { it.name == stored } ?: default
        val ACCEPTED_TERMS = intPreferencesKey("accepted_terms_version")
    }
}
