// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.navigation3.runtime.NavKey
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.navigation.AuthorizedAppsKey
import social.aloha.feature.settings.NEXTCLOUD_SECTION_KEY
import social.aloha.feature.settings.NEXTCLOUD_SECTION_ORDER
import social.aloha.feature.settings.R as SettingsR
import social.aloha.feature.settings.SettingsDestination

/** The Nextcloud section's own key, where connecting the Nextcloud starts. */
internal const val NEXTCLOUD_SECTION = NEXTCLOUD_SECTION_KEY

// right after the Nextcloud section, which connects what these pages need
private const val AUTHORIZED_APPS_ORDER = NEXTCLOUD_SECTION_ORDER + 4

/**
 * What only the Nextcloud session reaches on Nextcloud Social, listed for every account there: a page
 * says itself when the Nextcloud is not connected yet.
 */
internal fun nextcloudDestinations(readerId: String, backStack: MutableList<NavKey>) = listOf(
    SettingsDestination("authorized-apps", AUTHORIZED_APPS_ORDER, SettingsR.string.apps_title, AlohaIcons.Apps) {
        backStack.push(AuthorizedAppsKey(readerId))
    },
)
