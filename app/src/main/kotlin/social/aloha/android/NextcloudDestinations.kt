// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.navigation.AccountExportKey
import social.aloha.core.navigation.AuthorizedAppsKey
import social.aloha.core.navigation.ChannelsKey
import social.aloha.core.navigation.LookingBackKey
import social.aloha.core.navigation.StatisticsKey
import social.aloha.feature.settings.R as SettingsR
import social.aloha.feature.settings.SettingsDestination

/** The Nextcloud section's own key, where connecting the Nextcloud starts. */
internal const val NEXTCLOUD_SECTION = "nextcloud"

// right after the Nextcloud section, which connects what these pages need
private const val LOOKING_BACK_ORDER = 901
private const val STATISTICS_ORDER = 902
private const val CHANNELS_ORDER = 903
private const val AUTHORIZED_APPS_ORDER = 904
private const val EXPORT_ORDER = 905

/**
 * What only the Nextcloud session reaches on Nextcloud Social, listed for every account there: a page
 * says itself when the Nextcloud is not connected yet.
 */
internal fun nextcloudDestinations(readerId: String, backStack: MutableList<NavKey>) = listOf(
    SettingsDestination("looking-back", LOOKING_BACK_ORDER, SettingsR.string.looking_back_title, AlohaIcons.OnThisDay) {
        backStack.push(LookingBackKey(readerId))
    },
    SettingsDestination("statistics", STATISTICS_ORDER, SettingsR.string.statistics_title, AlohaIcons.Statistics) {
        backStack.push(StatisticsKey(readerId))
    },
    SettingsDestination("channels", CHANNELS_ORDER, SettingsR.string.channels_title, AlohaIcons.Video) {
        backStack.push(ChannelsKey(readerId))
    },
    SettingsDestination("authorized-apps", AUTHORIZED_APPS_ORDER, SettingsR.string.apps_title, AlohaIcons.Apps) {
        backStack.push(AuthorizedAppsKey(readerId))
    },
    SettingsDestination("export", EXPORT_ORDER, SettingsR.string.export_title, AlohaIcons.Download) {
        backStack.push(AccountExportKey(readerId))
    },
)

/** Video mode's way to the reader's own channels, where the server has them. */
@Composable
internal fun ChannelsLink(onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.s), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onOpen) {
            Icon(AlohaIcons.Video, contentDescription = null, Modifier.padding(end = AlohaSpacing.xs))
            Text(stringResource(R.string.video_your_channels))
        }
    }
}
