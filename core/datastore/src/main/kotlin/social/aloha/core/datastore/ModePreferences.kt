// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.model.FeedMode
import social.aloha.core.model.ModeChoices

/**
 * The device's choices about the modes: which the navigation shows and where, how Photos is laid out,
 * and whether videos make a sound. Kept in the same file as [AppPreferences], for the same device.
 */
public class ModePreferences(private val store: DataStore<Preferences>) {
    /**
     * Whether videos play without sound, as they start until the person turns it on; the choice holds
     * across sessions and modes. Autoplay means picture: sound is the person's choice.
     */
    public val videosMuted: Flow<Boolean> = store.data.map { it[VIDEOS_MUTED] ?: true }

    public suspend fun setVideosMuted(muted: Boolean) {
        store.edit { it[VIDEOS_MUTED] = muted }
    }

    /** Whether a short starts over when it ends, as it does until the person says not to. */
    public val loopShorts: Flow<Boolean> = store.data.map { it[LOOP_SHORTS] ?: true }

    public suspend fun setLoopShorts(loop: Boolean) {
        store.edit { it[LOOP_SHORTS] = loop }
    }

    /**
     * Whether a short plays as it comes into view on mobile data too; off, it waits for a tap there.
     * On Wi-Fi it always plays.
     */
    public val autoplayOnMobileData: Flow<Boolean> = store.data.map { it[AUTOPLAY_MOBILE] ?: true }

    public suspend fun setAutoplayOnMobileData(autoplay: Boolean) {
        store.edit { it[AUTOPLAY_MOBILE] = autoplay }
    }

    /** Which modes the navigation shows, News and Audio among them once turned on, and where on a phone. */
    public val modeChoices: Flow<ModeChoices> = store.data.map { stored ->
        val optional = stored[OPTIONAL_MODES].orEmpty().mapNotNull(::modeOf).toSet()
        val phone = stored[PHONE_MODES]?.split(',')?.mapNotNull(::modeOf)?.takeIf { it.size == ModeChoices.PHONE.size }
        ModeChoices(optional, phone ?: ModeChoices.PHONE)
    }

    public suspend fun setModeChoices(choices: ModeChoices) {
        store.edit {
            it[OPTIONAL_MODES] = choices.optional.map(FeedMode::key).toSet()
            it[PHONE_MODES] = choices.phone.joinToString(",") { mode -> mode.key }
        }
    }

    /**
     * Whether the optional modes were offered once, after the first account; someone who already chose
     * modes before the offer existed counts as offered.
     */
    public val modesOffered: Flow<Boolean> = store.data.map { it[MODES_OFFERED] ?: (it[OPTIONAL_MODES] != null) }

    public suspend fun modesWereOffered() {
        store.edit { it[MODES_OFFERED] = true }
    }

    /** Whether Photos shows as a grid, as it does until the person picks the feed. */
    public val photosGrid: Flow<Boolean> = store.data.map { it[PHOTOS_GRID] ?: true }

    public suspend fun setPhotosGrid(grid: Boolean) {
        store.edit { it[PHOTOS_GRID] = grid }
    }

    private companion object {
        val PHOTOS_GRID = booleanPreferencesKey("photos_grid")
        val MODES_OFFERED = booleanPreferencesKey("modes_offered")
        val LOOP_SHORTS = booleanPreferencesKey("loop_shorts")
        val AUTOPLAY_MOBILE = booleanPreferencesKey("autoplay_on_mobile_data")
        val VIDEOS_MUTED = booleanPreferencesKey("videos_muted")
        val OPTIONAL_MODES = stringSetPreferencesKey("optional_modes")
        val PHONE_MODES = stringPreferencesKey("phone_modes")

        // a mode this build does not know reads as none
        fun modeOf(key: String): FeedMode? = FeedMode.entries.firstOrNull { it.key == key }
    }
}
