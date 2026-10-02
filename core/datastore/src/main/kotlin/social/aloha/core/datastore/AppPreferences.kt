// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.model.AccentSource
import social.aloha.core.model.Appearance
import social.aloha.core.model.AppearanceContrast
import social.aloha.core.model.AppearanceMode
import social.aloha.core.model.QuietHours
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

    /** The accounts whose server pushes to this device. */
    public val pushAccounts: Flow<Set<String>> = store.data.map { it[PUSH_ACCOUNTS].orEmpty() }

    public suspend fun setPush(accountId: String, active: Boolean) {
        store.edit {
            val now = it[PUSH_ACCOUNTS].orEmpty()
            it[PUSH_ACCOUNTS] = if (active) now + accountId else now - accountId
        }
    }

    /** The endpoint push registration [instance] was last subscribed with, so the same one is not sent again. */
    public fun pushEndpoint(instance: String): Flow<String?> = store.data.map {
        it[stringPreferencesKey(ENDPOINT + instance)]
    }

    public suspend fun setPushEndpoint(instance: String, endpoint: String?) {
        val key = stringPreferencesKey(ENDPOINT + instance)
        store.edit { if (endpoint == null) it.remove(key) else it[key] = endpoint }
    }

    /** Whether the person was asked to allow notifications; a second ask goes to the system settings. */
    public val askedForNotifications: Flow<Boolean> = store.data.map { it[ASKED_NOTIFICATIONS] ?: false }

    public suspend fun setAskedForNotifications() {
        store.edit { it[ASKED_NOTIFICATIONS] = true }
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

    /** How the app looks; each part a later build does not know reads as its default. */
    public val appearance: Flow<Appearance> = store.data.map {
        Appearance(
            mode = choice(it[THEME_MODE], AppearanceMode.System),
            contrast = choice(it[THEME_CONTRAST], AppearanceContrast.System),
            black = it[THEME_BLACK] ?: false,
            accent = choice(it[THEME_ACCENT], AccentSource.Server),
            customAccent = it[THEME_CUSTOM_ACCENT] ?: Appearance.DEFAULT_CUSTOM_ACCENT,
        )
    }

    public suspend fun setAppearance(appearance: Appearance) {
        store.edit {
            it[THEME_MODE] = appearance.mode.name
            it[THEME_CONTRAST] = appearance.contrast.name
            it[THEME_BLACK] = appearance.black
            it[THEME_ACCENT] = appearance.accent.name
            it[THEME_CUSTOM_ACCENT] = appearance.customAccent
        }
    }

    private companion object {
        val SWIPE_END = stringPreferencesKey("swipe_towards_end")
        val SWIPE_START = stringPreferencesKey("swipe_towards_start")
        val ACTIVE_ACCOUNT = stringPreferencesKey("active_account_id")
        val WARN_DESCRIPTION = booleanPreferencesKey("warn_missing_description")
        val TAG_SHORTS = booleanPreferencesKey("tag_shorts")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val QUIET_FROM = intPreferencesKey("quiet_from_hour")
        val QUIET_UNTIL = intPreferencesKey("quiet_until_hour")
        val ASKED_NOTIFICATIONS = booleanPreferencesKey("asked_for_notifications")
        val PUSH_ACCOUNTS = stringSetPreferencesKey("push_accounts")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_CONTRAST = stringPreferencesKey("theme_contrast")
        val THEME_BLACK = booleanPreferencesKey("theme_black")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val THEME_CUSTOM_ACCENT = intPreferencesKey("theme_custom_accent")
        const val ENDPOINT = "push_endpoint:"

        inline fun <reified T : Enum<T>> choice(stored: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == stored } ?: default

        /** A value a later build wrote, or none at all, reads as the default. */
        fun swipe(stored: String?, default: SwipeAction): SwipeAction =
            SwipeAction.entries.firstOrNull { it.name == stored } ?: default
        val ACCEPTED_TERMS = intPreferencesKey("accepted_terms_version")
    }
}
