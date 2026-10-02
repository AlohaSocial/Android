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
 * @param showCounts the numbers beside reply, boost and favourite.
 * @param unreadBadge the count on Notifications in the navigation.
 */
public data class ReadingStyle(
    val compact: Boolean = false,
    val serif: Boolean = false,
    val relaxed: Boolean = false,
    val roundedAvatars: Boolean = false,
    val showCounts: Boolean = true,
    val unreadBadge: Boolean = true,
)
