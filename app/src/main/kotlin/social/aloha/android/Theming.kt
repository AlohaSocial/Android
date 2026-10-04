// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import social.aloha.core.designsystem.ColourSource
import social.aloha.core.designsystem.ContrastPreference
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.AccentSource
import social.aloha.core.model.Appearance
import social.aloha.core.model.AppearanceContrast
import social.aloha.core.model.AppearanceMode
import social.aloha.core.model.NextcloudTheme

/**
 * The theme the reader chose, with the colour their Nextcloud wears where they chose the server's;
 * a server that wears none (Mastodon, or Theming turned off) leaves the app's own.
 */
internal fun themeOf(appearance: Appearance, server: NextcloudTheme?): ThemeSettings = ThemeSettings(
    mode = when (appearance.mode) {
        AppearanceMode.System -> ThemeMode.System
        AppearanceMode.Light -> ThemeMode.Light
        AppearanceMode.Dark -> ThemeMode.Dark
    },
    contrast = when (appearance.contrast) {
        AppearanceContrast.System -> ContrastPreference.FollowSystem
        AppearanceContrast.Standard -> ContrastPreference.Standard
        AppearanceContrast.High -> ContrastPreference.High
    },
    black = appearance.black,
    colourSource = when (appearance.accent) {
        AccentSource.Server -> server?.colourHex?.let(::argb)?.let(ColourSource::Server) ?: ColourSource.Seed
        AccentSource.Wallpaper -> ColourSource.Dynamic
        AccentSource.App -> ColourSource.Seed
        AccentSource.Custom -> ColourSource.Server(appearance.customAccent)
    },
)

/** `#rrggbb`, as [NextcloudTheme] normalises it, as an opaque ARGB colour. */
private fun argb(hex: String): Int? = hex.removePrefix("#").toIntOrNull(HEX)?.let { it or OPAQUE }

private const val HEX = 16
private const val OPAQUE = 0xFF000000.toInt()
