// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * A server's custom emoji.
 *
 * @property staticUrl the still frame. Nextcloud Social serves the same file as [url], since no
 *   separate still rendering of an upload exists.
 * @property category omitted rather than null when there is none; the picker groups by it.
 */
@Serializable
public data class CustomEmoji(
    val shortcode: String,
    val url: String? = null,
    val staticUrl: String? = null,
    val visibleInPicker: Boolean = true,
    val category: String? = null,
) {
    /** The still image for a reader who turned animations off; [url] where the server has no other. */
    val stillUrl: String? get() = staticUrl ?: url
}
