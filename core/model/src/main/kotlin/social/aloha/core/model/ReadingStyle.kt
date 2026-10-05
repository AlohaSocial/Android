// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * How posts read, as chosen in Settings, Reading.
 *
 * @param compact rows closer together, so more fit on a screen.
 * @param serif a post's text in a serif face.
 * @param relaxed more room between a post's lines.
 * @param roundedAvatars rounded squares instead of circles, for every avatar.
 * @param showCounts the popularity numbers: beside reply, boost, favourite and reactions, a profile's posts,
 *   following and followers, how many others did something in a grouped notification, a video's views and
 *   likes, a year's followers. Poll results, unread counts and limits are information and always show.
 * @param unreadBadge the count on Notifications in the navigation.
 * @param haptics a light tick under the finger as a post is boosted, favourited or bookmarked.
 * @param fullPicturesOnMobileData the viewer loads a picture at full size on mobile data too; off, its
 *   preview there, as lists show it.
 * @param postLines a hairline between posts in every list of them.
 * @param revealWarnings in a thread, whether opening one content warning opens the same warning elsewhere.
 * @param collapseLong a post's text taller than a screenful's third is clipped behind Expand.
 * @param missingAltBadge a "no ALT" badge on media posted without a description.
 * @param previewless media listed as rows (kind, description, sensitive) instead of drawn, to save data and calm.
 * @param reduceMotion no animation anywhere in the app, whatever the system's animation scale.
 * @param textScale a post's text, names and times at this share of their size, from [MIN_TEXT_SCALE] to
 *   [MAX_TEXT_SCALE], on top of the system's font size.
 * @param absoluteTimes a post's time as a clock or a date, "14:32" or "3 Oct", instead of its age, "5m".
 * @param boostCarousel three or more boosts in a row on Home fold into one row of cards, a swipe apart.
 */
public data class ReadingStyle(
    val compact: Boolean = false,
    val serif: Boolean = false,
    val relaxed: Boolean = false,
    val roundedAvatars: Boolean = false,
    val showCounts: Boolean = true,
    val unreadBadge: Boolean = true,
    val haptics: Boolean = true,
    val fullPicturesOnMobileData: Boolean = true,
    val postLines: Boolean = true,
    val revealWarnings: WarningReveal = WarningReveal.Never,
    val collapseLong: Boolean = true,
    val missingAltBadge: Boolean = false,
    val previewless: Boolean = false,
    val reduceMotion: Boolean = false,
    val textScale: Float = 1f,
    val absoluteTimes: Boolean = false,
    val boostCarousel: Boolean = false,
) {
    public companion object {
        public const val MIN_TEXT_SCALE: Float = 0.8f
        public const val MAX_TEXT_SCALE: Float = 1.5f
    }
}

/** Which other posts in a thread open with a content warning the reader opened: those with the same warning. */
public enum class WarningReveal { Never, SameAuthor, Everyone }
