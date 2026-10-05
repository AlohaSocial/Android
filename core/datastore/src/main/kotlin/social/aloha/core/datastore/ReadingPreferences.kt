// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.model.ReadingStyle
import social.aloha.core.model.WarningReveal

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

    /** Whether a tap on Home's feed switcher moves to the next pinned feed; off, it lists them. */
    public val titleNextFeed: Flow<Boolean> = store.data.map { it[TITLE_NEXT_FEED] ?: false }

    public suspend fun setTitleNextFeed(next: Boolean) {
        store.edit { it[TITLE_NEXT_FEED] = next }
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
            haptics = it[HAPTICS] ?: true,
            fullPicturesOnMobileData = it[FULL_PICTURES] ?: true,
            postLines = it[POST_LINES] ?: true,
            revealWarnings = WarningReveal.entries.firstOrNull { reveal -> reveal.name == it[REVEAL_WARNINGS] }
                ?: WarningReveal.Never,
            collapseLong = it[COLLAPSE_LONG] ?: true,
            missingAltBadge = it[MISSING_ALT] ?: false,
            previewless = it[PREVIEWLESS] ?: false,
            reduceMotion = it[REDUCE_MOTION] ?: false,
            textScale = (it[TEXT_SCALE] ?: 1f).coerceIn(ReadingStyle.MIN_TEXT_SCALE, ReadingStyle.MAX_TEXT_SCALE),
            absoluteTimes = it[ABSOLUTE_TIMES] ?: false,
            boostCarousel = it[BOOST_CAROUSEL] ?: false,
            showTrends = it[SHOW_TRENDS] ?: true,
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
            it[HAPTICS] = style.haptics
            it[FULL_PICTURES] = style.fullPicturesOnMobileData
            it[POST_LINES] = style.postLines
            it[REVEAL_WARNINGS] = style.revealWarnings.name
            it[COLLAPSE_LONG] = style.collapseLong
            it[MISSING_ALT] = style.missingAltBadge
            it[PREVIEWLESS] = style.previewless
            it[REDUCE_MOTION] = style.reduceMotion
            it[TEXT_SCALE] = style.textScale
            it[ABSOLUTE_TIMES] = style.absoluteTimes
            it[BOOST_CAROUSEL] = style.boostCarousel
            it[SHOW_TRENDS] = style.showTrends
        }
    }

    private companion object {
        val RESTORE_POSITION = booleanPreferencesKey("restore_position")
        val NEW_POSTS_PILL = booleanPreferencesKey("new_posts_pill")
        val TITLE_NEXT_FEED = booleanPreferencesKey("title_next_feed")
        val COMPACT = booleanPreferencesKey("reading_compact")
        val SERIF = booleanPreferencesKey("reading_serif")
        val RELAXED = booleanPreferencesKey("reading_relaxed")
        val ROUNDED_AVATARS = booleanPreferencesKey("reading_rounded_avatars")
        val SHOW_COUNTS = booleanPreferencesKey("reading_show_counts")
        val UNREAD_BADGE = booleanPreferencesKey("reading_unread_badge")
        val HAPTICS = booleanPreferencesKey("haptics")
        val FULL_PICTURES = booleanPreferencesKey("full_pictures_on_mobile_data")
        val POST_LINES = booleanPreferencesKey("reading_post_lines")
        val REVEAL_WARNINGS = stringPreferencesKey("reading_reveal_warnings")
        val COLLAPSE_LONG = booleanPreferencesKey("reading_collapse_long")
        val MISSING_ALT = booleanPreferencesKey("reading_missing_alt_badge")
        val PREVIEWLESS = booleanPreferencesKey("reading_previewless")
        val REDUCE_MOTION = booleanPreferencesKey("reading_reduce_motion")
        val TEXT_SCALE = floatPreferencesKey("reading_text_scale")
        val ABSOLUTE_TIMES = booleanPreferencesKey("reading_absolute_times")
        val BOOST_CAROUSEL = booleanPreferencesKey("reading_boost_carousel")
        val SHOW_TRENDS = booleanPreferencesKey("reading_show_trends")
    }
}
