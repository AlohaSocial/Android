// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaPreviews
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.navigation.HomeKey
import social.aloha.core.navigation.NotificationsKey
import social.aloha.core.navigation.PhotosKey
import social.aloha.core.navigation.ProfileKey
import social.aloha.core.navigation.ShortsKey
import social.aloha.core.navigation.TopLevelKey
import social.aloha.core.navigation.VideoKey

private data class TopLevelDestination(
    val key: TopLevelKey,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * The navigation suite's items: five, the most a Material 3 navigation bar
 * holds. Profile belongs to the account avatar in the top app bar, not to the
 * bar.
 */
private val destinations = listOf(
    TopLevelDestination(HomeKey, R.string.destination_home, AlohaIcons.Home, AlohaIcons.HomeSelected),
    TopLevelDestination(PhotosKey, R.string.destination_photos, AlohaIcons.Photos, AlohaIcons.PhotosSelected),
    TopLevelDestination(VideoKey, R.string.destination_video, AlohaIcons.Video, AlohaIcons.VideoSelected),
    TopLevelDestination(ShortsKey, R.string.destination_shorts, AlohaIcons.Shorts, AlohaIcons.ShortsSelected),
    TopLevelDestination(
        NotificationsKey,
        R.string.destination_notifications,
        AlohaIcons.Notifications,
        AlohaIcons.NotificationsSelected,
    ),
)

/**
 * The shell: a navigation suite (bar, rail or drawer by window size) around a
 * Navigation 3 display. Every destination is a placeholder until its phase.
 */
@Composable
fun AlohaApp() {
    val backStack = rememberNavBackStack(HomeKey)
    val current = backStack.lastOrNull()
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            destinations.forEach { destination ->
                val selected = destination.key == current
                item(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            backStack.clear()
                            backStack.add(destination.key)
                        }
                    },
                    icon = {
                        Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
                    },
                    label = { Text(stringResource(destination.label), maxLines = 1) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeKey> { Placeholder(stringResource(R.string.destination_home)) }
                entry<PhotosKey> { Placeholder(stringResource(R.string.destination_photos)) }
                entry<VideoKey> { Placeholder(stringResource(R.string.destination_video)) }
                entry<ShortsKey> { Placeholder(stringResource(R.string.destination_shorts)) }
                entry<NotificationsKey> { Placeholder(stringResource(R.string.destination_notifications)) }
                entry<ProfileKey> { Placeholder(stringResource(R.string.destination_profile)) }
            },
        )
    }
}

@Composable
private fun Placeholder(title: String) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(AlohaSpacing.m)
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
    }
}

@AlohaPreviews
@Composable
private fun AlohaAppPreview() {
    AlohaTheme { AlohaApp() }
}
