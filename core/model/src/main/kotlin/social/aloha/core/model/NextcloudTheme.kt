// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * The colour a Nextcloud is wearing, read from its public `theming` capability at the Nextcloud root
 * (not the Mastodon API base). An administrator who themed their Nextcloud has already chosen the
 * colour their people know the server by.
 *
 * Every hex value is normalised to `#rrggbb` or absent.
 *
 * @property elementBrightHex the primary adjusted to stay legible on a light background.
 * @property elementDarkHex the primary adjusted to stay legible on a dark background.
 * @property textHex what Nextcloud puts on the primary colour: `#ffffff` or `#000000`.
 */
@Serializable
public data class NextcloudTheme(
    val name: String = "",
    val slogan: String = "",
    val colourHex: String? = null,
    val elementBrightHex: String? = null,
    val elementDarkHex: String? = null,
    val textHex: String? = null,
) {
    /** Whether this says anything about a colour; with the Theming app disabled the keys are absent. */
    val hasColour: Boolean get() = colourHex != null

    /** The variant for a background of this kind, falling back to the raw colour the administrator chose. */
    public fun hex(onDarkBackground: Boolean): String? =
        (if (onDarkBackground) elementDarkHex else elementBrightHex) ?: colourHex

    public companion object {
        private const val SHORT_HEX = 3
        private const val LONG_HEX = 6

        /** `#abc` and `abcdef` both become six-digit `#aabbcc`; anything else is null, not a colour nobody chose. */
        public fun normalise(raw: String?): String? {
            val text = raw?.trim()?.lowercase()?.removePrefix("#").orEmpty()
            val expanded = if (text.length == SHORT_HEX) text.map { "$it$it" }.joinToString("") else text
            val valid = expanded.length == LONG_HEX && expanded.all { it in '0'..'9' || it in 'a'..'f' }
            return if (valid) "#$expanded" else null
        }
    }
}
