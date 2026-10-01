// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

/** What a short can ask for: each gesture's action, and the same from the rail and the menu. */
internal interface ShortsActions {
    fun onFavourite(short: ShortUi)
    fun onBoost(short: ShortUi)
    fun onComments(short: ShortUi)
    fun onShare(short: ShortUi)
    fun onProfile(short: ShortUi)
    fun onMuted(muted: Boolean)
    fun onReport(short: ShortUi)
}
