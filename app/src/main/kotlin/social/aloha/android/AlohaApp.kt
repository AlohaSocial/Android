// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import androidx.window.core.layout.WindowSizeClass
import java.util.UUID
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaPreviews
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.badgeCount
import social.aloha.core.model.FeedMode
import social.aloha.core.model.ModeChoices
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.AddToAlbumKey
import social.aloha.core.navigation.AlbumKey
import social.aloha.core.navigation.AlbumsKey
import social.aloha.core.navigation.AudioKey
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.navigation.EditProfileKey
import social.aloha.core.navigation.HomeKey
import social.aloha.core.navigation.MediaViewerKey
import social.aloha.core.navigation.NewsKey
import social.aloha.core.navigation.NotificationPolicyKey
import social.aloha.core.navigation.NotificationRequestsKey
import social.aloha.core.navigation.NotificationsKey
import social.aloha.core.navigation.PeopleKey
import social.aloha.core.navigation.PeopleKind
import social.aloha.core.navigation.PhotoExploreKey
import social.aloha.core.navigation.PhotosKey
import social.aloha.core.navigation.ProfileKey
import social.aloha.core.navigation.ReportKey
import social.aloha.core.navigation.RouteResolver
import social.aloha.core.navigation.ScheduledPostsKey
import social.aloha.core.navigation.SettingsKey
import social.aloha.core.navigation.SettingsSectionKey
import social.aloha.core.navigation.ShortsKey
import social.aloha.core.navigation.StatusListKey
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.navigation.TagKey
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.navigation.TopLevelKey
import social.aloha.core.navigation.VideoKey
import social.aloha.core.navigation.WatchKey
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.openInBrowser
import social.aloha.feature.audio.AudioRoute
import social.aloha.feature.audio.MiniPlayer
import social.aloha.feature.composer.ComposerRoute
import social.aloha.feature.composer.DraftsRoute
import social.aloha.feature.composer.ScheduledPostsRoute
import social.aloha.feature.mediaviewer.MediaViewerRoute
import social.aloha.feature.notifications.NotificationsRoute
import social.aloha.feature.notifications.PolicyRoute
import social.aloha.feature.notifications.RequestsRoute
import social.aloha.feature.photos.AddToAlbumRoute
import social.aloha.feature.photos.AlbumRoute
import social.aloha.feature.photos.AlbumsRoute
import social.aloha.feature.photos.PhotoExploreRoute
import social.aloha.feature.profile.EditProfileRoute
import social.aloha.feature.profile.PeopleRoute
import social.aloha.feature.profile.ProfileNavigation
import social.aloha.feature.profile.ProfileRoute
import social.aloha.feature.profile.ReportRoute
import social.aloha.feature.settings.SettingsPlaceholder
import social.aloha.feature.settings.SettingsRoute
import social.aloha.feature.settings.SettingsSectionRoute
import social.aloha.feature.shorts.ShortsRoute
import social.aloha.feature.stories.StoriesRail
import social.aloha.feature.thread.StatusListRoute
import social.aloha.feature.thread.ThreadNavigation
import social.aloha.feature.thread.ThreadRoute
import social.aloha.feature.timeline.TagRoute
import social.aloha.feature.timeline.TimelineFeed
import social.aloha.feature.timeline.TimelineRoute
import social.aloha.feature.video.ContinueWatching
import social.aloha.feature.video.WatchRoute

private data class TopLevelDestination(
    val key: TopLevelKey,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** The modes the navigation shows: on a rail or drawer, and in a phone's bar of three. */
data class ModeNavigation(val wide: List<FeedMode> = ModeChoices.PHONE, val phone: List<FeedMode> = ModeChoices.PHONE)

/**
 * The navigation suite's items: Home, the modes, Notifications. A phone's bar holds five, the most a
 * Material 3 navigation bar takes, so it shows the modes chosen for its three slots; a rail or drawer
 * shows all. Profile belongs to the account avatar in the top app bar, not to the bar.
 */
private fun destinationsFor(type: NavigationSuiteType, modes: ModeNavigation): List<TopLevelDestination> {
    val bar = type == NavigationSuiteType.NavigationBar || type == NavigationSuiteType.ShortNavigationBarCompact
    return listOf(HOME) + (if (bar) modes.phone else modes.wide).map(::destinationOf) + NOTIFICATIONS
}

private fun destinationOf(mode: FeedMode): TopLevelDestination = when (mode) {
    FeedMode.Home -> HOME

    FeedMode.Photos -> TopLevelDestination(
        PhotosKey,
        R.string.destination_photos,
        AlohaIcons.Photos,
        AlohaIcons.PhotosSelected,
    )

    FeedMode.Video -> TopLevelDestination(
        VideoKey,
        R.string.destination_video,
        AlohaIcons.Video,
        AlohaIcons.VideoSelected,
    )

    FeedMode.Shorts -> TopLevelDestination(
        ShortsKey,
        R.string.destination_shorts,
        AlohaIcons.Shorts,
        AlohaIcons.ShortsSelected,
    )

    FeedMode.News -> TopLevelDestination(NewsKey, R.string.destination_news, AlohaIcons.News, AlohaIcons.NewsSelected)

    FeedMode.Audio -> TopLevelDestination(
        AudioKey,
        R.string.destination_audio,
        AlohaIcons.Audio,
        AlohaIcons.AudioSelected,
    )
}

private val HOME = TopLevelDestination(HomeKey, R.string.destination_home, AlohaIcons.Home, AlohaIcons.HomeSelected)
private val NOTIFICATIONS = TopLevelDestination(
    NotificationsKey,
    R.string.destination_notifications,
    AlohaIcons.Notifications,
    AlohaIcons.NotificationsSelected,
)

/**
 * The shell: a navigation suite (bar, rail or drawer by window size) around a
 * Navigation 3 display. From medium widths the display is list-detail: a
 * destination stays on the left while the post, profile or list it opened reads
 * on the right; on a phone each takes the screen in turn. [home] draws the home
 * destination; destinations not yet built are placeholders.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AlohaApp(
    readerId: String,
    serverAccountId: String,
    pendingLink: String? = null,
    onPendingLinkTaken: () -> Unit = {},
    pendingDestination: NavKey? = null,
    onPendingDestinationTaken: () -> Unit = {},
    unreadNotifications: Int = 0,
    resolveLink: suspend (address: String, fromPost: Boolean) -> NavKey? = { _, _ -> null },
    accountButton: @Composable (onProfile: () -> Unit, onSettings: () -> Unit) -> Unit = { _, _ -> },
    modes: ModeNavigation = ModeNavigation(),
    timeline: @Composable (TimelineFeed, StatusNavigation, accountButton: @Composable () -> Unit) -> Unit =
        { feed, navigation, button -> ModeTimeline(feed, navigation, button) },
    nowPlaying: @Composable (onOpen: (statusId: String) -> Unit) -> Unit = { MiniPlayer(onOpen = it) },
) {
    val backStack = rememberNavBackStack(HomeKey)
    // a detail opened from a destination keeps that destination selected
    val current = backStack.lastOrNull { it is TopLevelKey }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // in the app where the link points at something the reader's server finds, else the browser
    val open: (String, Boolean) -> Unit = { address, fromPost ->
        scope.launch {
            val destination = runCatching { resolveLink(address, fromPost) }.getOrNull()
            ensureActive()
            if (destination != null) {
                backStack.push(destination)
            } else {
                RouteResolver.browsable(address)?.let { openInBrowser(context, it) }
            }
        }
    }
    // the media viewer lies over the whole shell, so dragging a picture away shows what was under it
    var viewing by rememberSaveable(stateSaver = ViewingSaver) { mutableStateOf<MediaViewerKey?>(null) }
    val statusNavigation = remember(backStack, readerId) {
        object : ThreadNavigation, ProfileNavigation {
            override fun openThread(statusId: String) {
                backStack.push(ThreadKey(readerId, statusId))
            }

            override fun openList(statusId: String, kind: StatusListKind) {
                backStack.push(StatusListKey(readerId, statusId, kind))
            }

            override fun openPeople(accountId: String, kind: PeopleKind) {
                backStack.push(PeopleKey(readerId, accountId, kind))
            }

            override fun back() {
                backStack.removeLastOrNull()
            }

            override fun editProfile() {
                backStack.push(EditProfileKey(readerId))
            }

            override fun openProfile(accountId: String?, acct: String?) {
                backStack.push(AccountKey(readerId, id = accountId, acct = acct))
            }

            override fun openTag(name: String) {
                backStack.push(TagKey(readerId, name))
            }

            override fun openWeb(url: String) = open(url, true)

            override fun openComposer(replyToId: String?) {
                // the draft's id goes with the key, so a composer restored after the app was stopped finds it
                backStack.push(ComposerKey(readerId, replyToId, draftId = UUID.randomUUID().toString()))
            }

            override fun report(accountId: String, handle: String, statusId: String?) {
                backStack.push(ReportKey(readerId, accountId, handle, statusId))
            }

            override fun addToAlbum(statusId: String) {
                backStack.push(AddToAlbumKey(readerId, statusId))
            }

            override fun openAlbums() {
                backStack.push(AlbumsKey(readerId))
            }

            override fun openMedia(statusId: String, index: Int) {
                viewing = MediaViewerKey(readerId, statusId, index)
            }

            override fun openVideo(statusId: String) {
                backStack.push(WatchKey(readerId, statusId))
            }

            override fun openPhotoExplore() {
                backStack.push(PhotoExploreKey(readerId))
            }

            override fun openAlbum(albumId: String, title: String, own: Boolean) {
                backStack.push(AlbumKey(readerId, albumId, title, own))
            }

            override fun editPost(statusId: String, redraft: Boolean) {
                backStack.push(
                    if (redraft) {
                        ComposerKey(readerId, redraftId = statusId, draftId = UUID.randomUUID().toString())
                    } else {
                        ComposerKey(readerId, editId = statusId)
                    },
                )
            }
        }
    }
    PushWhenAsked(pendingDestination, onPendingDestinationTaken) { backStack.push(it) }
    LaunchedEffect(pendingLink) {
        pendingLink?.let {
            onPendingLinkTaken()
            open(it, false)
        }
    }
    val adaptive = currentWindowAdaptiveInfoV2()
    // a rail on medium widths, the full drawer with labels once there is room for it
    val suiteType = if (adaptive.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
        )
    ) {
        NavigationSuiteType.NavigationDrawer
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptive)
    }
    val panes = rememberListDetailSceneStrategy<NavKey>()
    Box(Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            layoutType = suiteType,
            navigationSuiteItems = {
                destinationsFor(suiteType, modes).forEach { destination ->
                    val selected = destination.key == current
                    item(
                        selected = selected,
                        badge = { DestinationBadge(destination.key, unreadNotifications) },
                        onClick = {
                            if (!selected) {
                                backStack.clear()
                                backStack.add(destination.key)
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.label), maxLines = 1) },
                    )
                }
            },
        ) {
            // the navigation already pads its side for the system bars; the screens must not pad it again
            Column(Modifier.consumeWindowInsets(suiteInsets(suiteType))) {
                NavDisplay(
                    modifier = Modifier.weight(1f),
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies = listOf(panes),
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        // home and each mode: the same account button, each timeline of its own
                        val modeEntry = @Composable { feed: TimelineFeed ->
                            timeline(feed, statusNavigation) {
                                accountButton({
                                    backStack.push(AccountKey(readerId, id = serverAccountId))
                                }, { backStack.push(SettingsKey) })
                            }
                        }
                        val listPane = ListDetailSceneStrategy.listPane(detailPlaceholder = { NothingOpen() })
                        entry<HomeKey>(metadata = listPane) { modeEntry(TimelineFeed.Home) }
                        entry<PhotosKey>(metadata = listPane) { modeEntry(TimelineFeed.Mode(FeedMode.Photos)) }
                        entry<VideoKey>(metadata = listPane) { modeEntry(TimelineFeed.Mode(FeedMode.Video)) }
                        entry<ShortsKey>(metadata = listPane) { modeEntry(TimelineFeed.Mode(FeedMode.Shorts)) }
                        entry<NewsKey>(metadata = listPane) { modeEntry(TimelineFeed.Mode(FeedMode.News)) }
                        entry<AudioKey>(metadata = listPane) { modeEntry(TimelineFeed.Mode(FeedMode.Audio)) }
                        entry<NotificationsKey>(
                            metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NothingOpen() }),
                        ) {
                            NotificationsRoute(
                                statusNavigation,
                                onPolicy = { backStack.push(NotificationPolicyKey(readerId)) },
                                onRequests = { backStack.push(NotificationRequestsKey(readerId)) },
                                navigationIcon = {
                                    accountButton({
                                        backStack.push(AccountKey(readerId, id = serverAccountId))
                                    }, { backStack.push(SettingsKey) })
                                },
                            )
                        }
                        entry<NotificationPolicyKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            PolicyRoute(it, onBack = { backStack.remove(it) })
                        }
                        entry<NotificationRequestsKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            RequestsRoute(
                                it,
                                onOpenProfile = { id -> statusNavigation.openProfile(id, null) },
                                onBack = { backStack.remove(it) },
                            )
                        }
                        entry<SettingsKey>(
                            metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                                SettingsPlaceholder()
                            }),
                        ) {
                            SettingsRoute(onBack = {
                                backStack.removeLastOrNull()
                            }, onSection = { backStack.push(SettingsSectionKey(it)) })
                        }
                        entry<SettingsSectionKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            SettingsSectionRoute(it.section, onBack = { backStack.removeLastOrNull() })
                        }
                        entry<ProfileKey> { ProfileRoute(AccountKey(readerId, id = serverAccountId), statusNavigation) }
                        // a screen that closes itself takes its own entry away, so a second tap never closes
                        // what is under it
                        entry<ComposerKey> {
                            ComposerRoute(
                                it,
                                onDone = { backStack.remove(it) },
                                onScheduledPosts = { backStack.push(ScheduledPostsKey(it.readerId)) },
                                onDrafts = { backStack.push(DraftsKey(it.readerId)) },
                            )
                        }
                        entry<DraftsKey> { drafts ->
                            DraftsRoute(
                                drafts,
                                onBack = { backStack.remove(drafts) },
                                onOpen = { id ->
                                    // the draft opens in place of the list, and of the composer the list was
                                    // opened from
                                    backStack.remove(drafts)
                                    if (backStack.lastOrNull() is ComposerKey) backStack.removeLastOrNull()
                                    backStack.push(ComposerKey(drafts.readerId, draftId = id))
                                },
                            )
                        }
                        entry<ReportKey> { ReportRoute(it, onDone = { backStack.remove(it) }) }
                        entry<EditProfileKey> { EditProfileRoute(it, onDone = { backStack.remove(it) }) }
                        entry<ScheduledPostsKey> { ScheduledPostsRoute(it, onBack = { backStack.remove(it) }) }
                        entry<AlbumsKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            AlbumsRoute(
                                it,
                                onOpen = { album, own -> statusNavigation.openAlbum(album.id, album.title, own) },
                                onBack = { backStack.remove(it) },
                            )
                        }
                        entry<AlbumKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            AlbumRoute(it, onOpen = statusNavigation::openThread, onBack = { backStack.remove(it) })
                        }
                        entry<AddToAlbumKey> { AddToAlbumRoute(it, onBack = { backStack.remove(it) }) }
                        entry<WatchKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            WatchRoute(it, statusNavigation, onBack = { backStack.remove(it) })
                        }
                        entry<PhotoExploreKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            PhotoExploreRoute(it, statusNavigation, onBack = { backStack.remove(it) })
                        }
                        entry<ThreadKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            ThreadRoute(it, statusNavigation)
                        }
                        entry<StatusListKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            StatusListRoute(it, statusNavigation)
                        }
                        entry<AccountKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            ProfileRoute(it, statusNavigation)
                        }
                        entry<PeopleKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            PeopleRoute(it, statusNavigation)
                        }
                        entry<TagKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                            TagRoute(it.name, statusNavigation, onBack = statusNavigation::back)
                        }
                    },
                )
                // the sound playing, docked above the navigation wherever the reader goes
                nowPlaying(statusNavigation::openThread)
            }
        }
        OpenViewer(viewing, statusNavigation) { viewing = null }
    }
}

/** The media viewer over the shell, while one is open. */
@Composable
private fun OpenViewer(viewing: MediaViewerKey?, navigation: StatusNavigation, onClose: () -> Unit) {
    viewing?.let { MediaViewerRoute(it, navigation, onClose = onClose) }
}

/** The open media viewer, kept through a process restart like the back stack. */
private val ViewingSaver = Saver<MediaViewerKey?, List<Any>>(
    save = { key -> key?.let { listOf(it.readerId, it.statusId, it.index) } },
    restore = { saved -> MediaViewerKey(saved[0] as String, saved[1] as String, saved[2] as Int) },
)

/** The detail pane before anything is opened in it. */
@Composable
private fun NothingOpen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.detail_nothing_open),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Home's timeline, or a mode's: Photos carries the stories rail above its own, Video what to carry on with. */
@Composable
private fun ModeTimeline(feed: TimelineFeed, navigation: StatusNavigation, accountButton: @Composable () -> Unit) {
    // Shorts is a pager of its own rather than a timeline of rows
    if (feed.mode == FeedMode.Shorts) return ShortsRoute(navigation, accountButton)
    // and Audio a list to play from
    if (feed.mode == FeedMode.Audio) return AudioRoute(navigation, accountButton)
    TimelineRoute(
        navigation,
        feed = feed,
        navigationIcon = accountButton,
        header = {
            when (feed.mode) {
                // until the story player, a poster on the rail opens their profile
                FeedMode.Photos -> StoriesRail(onOpen = { navigation.openProfile(it, null) })

                FeedMode.Video -> ContinueWatching(onOpen = navigation::openVideo)

                else -> Unit
            }
        },
    )
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
    AlohaTheme {
        AlohaApp("preview", "1", timeline = { _, _, _ -> Placeholder(stringResource(R.string.destination_home)) })
    }
}

/** Opens [key] unless it is already on screen: a second copy would make back seem to do nothing. */
internal fun MutableList<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** The system bar edge [type] takes up: the bottom for a bar, the start for a rail or drawer. */
@Composable
private fun suiteInsets(type: NavigationSuiteType): WindowInsets = when (type) {
    NavigationSuiteType.NavigationBar,
    NavigationSuiteType.ShortNavigationBarCompact,
    NavigationSuiteType.ShortNavigationBarMedium,
    -> WindowInsets.systemBars.only(WindowInsetsSides.Bottom)

    NavigationSuiteType.None -> WindowInsets(0)

    else -> WindowInsets.systemBars.only(WindowInsetsSides.Start)
}

/** Opens [destination] once it is asked for from outside the app, and says it was taken. */
@Composable
private fun PushWhenAsked(destination: NavKey?, onTaken: () -> Unit, push: (NavKey) -> Unit) {
    LaunchedEffect(destination) {
        if (destination != null) {
            onTaken()
            push(destination)
        }
    }
}

/** The unread notifications on their destination; nothing on the others, nor with none unread. */
@Composable
private fun DestinationBadge(key: TopLevelKey, unreadNotifications: Int) {
    if (key == NotificationsKey && unreadNotifications > 0) Badge { Text(badgeCount(unreadNotifications)) }
}
