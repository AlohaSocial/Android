// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.designsystem.ColourSource
import social.aloha.core.designsystem.ContrastPreference
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.model.AccentSource
import social.aloha.core.model.Appearance
import social.aloha.core.model.AppearanceContrast
import social.aloha.core.model.AppearanceMode
import social.aloha.core.model.NextcloudTheme

class ThemingTest {
    private val nextcloud = NextcloudTheme(colourHex = "#0082c9")

    @Test
    fun `out of the box the app wears the colour the reader's Nextcloud wears`() {
        assertEquals(ColourSource.Server(0xFF0082C9.toInt()), themeOf(Appearance(), nextcloud).colourSource)
    }

    @Test
    fun `a server that wears no colour leaves the app's own`() {
        assertEquals(ColourSource.Seed, themeOf(Appearance(), null).colourSource)
        assertEquals(ColourSource.Seed, themeOf(Appearance(), NextcloudTheme()).colourSource)
    }

    @Test
    fun `the reader's own choices win over the server`() {
        val chosen = Appearance(
            mode = AppearanceMode.Dark,
            contrast = AppearanceContrast.High,
            black = true,
            accent = AccentSource.Custom,
            customAccent = 0xFF6750A4.toInt(),
        )
        val theme = themeOf(chosen, nextcloud)
        assertEquals(ThemeMode.Dark, theme.mode)
        assertEquals(ContrastPreference.High, theme.contrast)
        assertEquals(true, theme.black)
        assertEquals(ColourSource.Server(0xFF6750A4.toInt()), theme.colourSource)
        assertEquals(
            ColourSource.Dynamic,
            themeOf(chosen.copy(accent = AccentSource.Wallpaper), nextcloud).colourSource,
        )
        assertEquals(ColourSource.Seed, themeOf(chosen.copy(accent = AccentSource.App), nextcloud).colourSource)
    }

    @Test
    fun `a colour per account wears the account's own accent, and the shared one where it has none`() {
        val custom = Appearance(accent = AccentSource.Custom, customAccent = 1, accentPerAccount = true)
        assertEquals(ColourSource.Server(2), themeOf(custom, null, accountAccent = 2).colourSource)
        assertEquals(ColourSource.Server(1), themeOf(custom, null, accountAccent = null).colourSource)
        assertEquals(ColourSource.Server(1), themeOf(custom.copy(accentPerAccount = false), null, 2).colourSource)
    }
}
