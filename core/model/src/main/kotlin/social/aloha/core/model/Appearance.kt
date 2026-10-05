// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * How the app looks, as the reader chose it in Settings, Appearance.
 *
 * @param black dark backgrounds pure black, for OLED screens; only while dark.
 * @param customAccent the colour the scheme grows from with [AccentSource.Custom], as `0xAARRGGBB`.
 * @param accentPerAccount with [AccentSource.Custom], each account keeps its own accent, so switching
 *   shows whose app it is; an account without one wears [customAccent].
 */
public data class Appearance(
    val mode: AppearanceMode = AppearanceMode.System,
    val contrast: AppearanceContrast = AppearanceContrast.System,
    val black: Boolean = false,
    val accent: AccentSource = AccentSource.Server,
    val customAccent: Int = DEFAULT_CUSTOM_ACCENT,
    val accentPerAccount: Boolean = false,
) {
    public companion object {
        /** The app's own coral, where a custom accent starts out. */
        public const val DEFAULT_CUSTOM_ACCENT: Int = 0xFFE0654A.toInt()
    }
}

public enum class AppearanceMode { System, Light, Dark }

/** System follows the device's contrast setting, from Android 14. */
public enum class AppearanceContrast { System, Standard, High }

/**
 * Where the colours come from. Server is the colour the reader's Nextcloud wears, and the app's own
 * where it wears none or the server is not a Nextcloud.
 */
public enum class AccentSource { Server, Wallpaper, App, Custom }
