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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaPreviews
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.HomeKey
import social.aloha.core.navigation.NotificationsKey
import social.aloha.core.navigation.PhotosKey
import social.aloha.core.navigation.ProfileKey
import social.aloha.core.navigation.ShortsKey
import social.aloha.core.navigation.StatusListKey
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.navigation.TagKey
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.navigation.TopLevelKey
import social.aloha.core.navigation.VideoKey
import social.aloha.core.ui.StatusNavigation
import social.aloha.feature.thread.StatusListRoute
import social.aloha.feature.thread.ThreadNavigation
import social.aloha.feature.thread.ThreadRoute
import social.aloha.feature.timeline.TimelineRoute

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
 * Navigation 3 display. [home] draws the home destination; destinations not yet
 * built are placeholders.
 */
@Composable
fun AlohaApp(readerId: String, home: @Composable (StatusNavigation) -> Unit = { TimelineRoute(it) }) {
    val backStack = rememberNavBackStack(HomeKey)
    // a detail opened from a destination keeps that destination selected
    val current = backStack.lastOrNull { it is TopLevelKey }
    val statusNavigation = remember(backStack, readerId) {
        object : ThreadNavigation {
            override fun openThread(statusId: String) {
                backStack.push(ThreadKey(readerId, statusId))
            }

            override fun openList(statusId: String, kind: StatusListKind) {
                backStack.push(StatusListKey(readerId, statusId, kind))
            }

            override fun back() {
                backStack.removeLastOrNull()
            }

            override fun openProfile(accountId: String?, acct: String?) {
                backStack.push(AccountKey(readerId, id = accountId, acct = acct))
            }

            override fun openTag(name: String) {
                backStack.push(TagKey(readerId, name))
            }
        }
    }
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
                entry<HomeKey> { home(statusNavigation) }
                entry<PhotosKey> { Placeholder(stringResource(R.string.destination_photos)) }
                entry<VideoKey> { Placeholder(stringResource(R.string.destination_video)) }
                entry<ShortsKey> { Placeholder(stringResource(R.string.destination_shorts)) }
                entry<NotificationsKey> { Placeholder(stringResource(R.string.destination_notifications)) }
                entry<ProfileKey> { Placeholder(stringResource(R.string.destination_profile)) }
                entry<ThreadKey> { ThreadRoute(it, statusNavigation) }
                entry<StatusListKey> { StatusListRoute(it, statusNavigation) }
                entry<AccountKey> { Placeholder(stringResource(R.string.destination_profile)) }
                entry<TagKey> { Placeholder("#${it.name}") }
            },
        )
    }
}

@Composable
internal fun Placeholder(title: String) {
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
    AlohaTheme { AlohaApp("preview", home = { Placeholder(stringResource(R.string.destination_home)) }) }
}

/** Opens [key] unless it is already on screen: a second copy would make back seem to do nothing. */
internal fun MutableList<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}
