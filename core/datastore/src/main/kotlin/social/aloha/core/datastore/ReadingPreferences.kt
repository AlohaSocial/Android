// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.model.ReadingStyle

/** How the reader reads the device's timelines, kept beside the app's other preferences. */
public class ReadingPreferences(private val store: DataStore<Preferences>) {
    /** Whether a timeline opens where it was left, as it does until the reader says otherwise. */
    public val restorePosition: Flow<Boolean> = store.data.map { it[RESTORE_POSITION] ?: true }

    public suspend fun setRestorePosition(restore: Boolean) {
        store.edit { it[RESTORE_POSITION] = restore }
    }

    /** Whether new posts wait behind a pill; off, they join the timeline as they arrive. */
    public val newPostsPill: Flow<Boolean> = store.data.map { it[NEW_POSTS_PILL] ?: true }

    public suspend fun setNewPostsPill(pill: Boolean) {
        store.edit { it[NEW_POSTS_PILL] = pill }
    }

    /** How posts read: density, face, line spacing, avatar shape, counts, the unread badge. */
    public val style: Flow<ReadingStyle> = store.data.map {
        ReadingStyle(
            compact = it[COMPACT] ?: false,
            serif = it[SERIF] ?: false,
            relaxed = it[RELAXED] ?: false,
            roundedAvatars = it[ROUNDED_AVATARS] ?: false,
            showCounts = it[SHOW_COUNTS] ?: true,
            unreadBadge = it[UNREAD_BADGE] ?: true,
        )
    }

    public suspend fun setStyle(style: ReadingStyle) {
        store.edit {
            it[COMPACT] = style.compact
            it[SERIF] = style.serif
            it[RELAXED] = style.relaxed
            it[ROUNDED_AVATARS] = style.roundedAvatars
            it[SHOW_COUNTS] = style.showCounts
            it[UNREAD_BADGE] = style.unreadBadge
        }
    }

    private companion object {
        val RESTORE_POSITION = booleanPreferencesKey("restore_position")
        val NEW_POSTS_PILL = booleanPreferencesKey("new_posts_pill")
        val COMPACT = booleanPreferencesKey("reading_compact")
        val SERIF = booleanPreferencesKey("reading_serif")
        val RELAXED = booleanPreferencesKey("reading_relaxed")
        val ROUNDED_AVATARS = booleanPreferencesKey("reading_rounded_avatars")
        val SHOW_COUNTS = booleanPreferencesKey("reading_show_counts")
        val UNREAD_BADGE = booleanPreferencesKey("reading_unread_badge")
    }
}
