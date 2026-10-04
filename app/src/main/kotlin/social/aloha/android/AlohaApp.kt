// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
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
import social.aloha.core.navigation.AnnouncementsKey
import social.aloha.core.navigation.AudioKey
import social.aloha.core.navigation.BlockedKey
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.navigation.ConversationsKey
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.navigation.EditProfileKey
import social.aloha.core.navigation.FilterEditKey
import social.aloha.core.navigation.FiltersKey
import social.aloha.core.navigation.HashtagsKey
import social.aloha.core.navigation.HomeKey
import social.aloha.core.navigation.InterestsKey
import social.aloha.core.navigation.ListKey
import social.aloha.core.navigation.ListMembersKey
import social.aloha.core.navigation.ListsKey
import social.aloha.core.navigation.MediaViewerKey
import social.aloha.core.navigation.ModerationKey
import social.aloha.core.navigation.NewMessageKey
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
import social.aloha.core.navigation.SavedKind
import social.aloha.core.navigation.SavedPostsKey
import social.aloha.core.navigation.ScheduledPostsKey
import social.aloha.core.navigation.SearchKey
import social.aloha.core.navigation.SettingsKey
import social.aloha.core.navigation.SettingsSectionKey
import social.aloha.core.navigation.ShortsKey
import social.aloha.core.navigation.StatusListKey
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.navigation.TagGroupKey
import social.aloha.core.navigation.TagKey
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.navigation.TopLevelKey
import social.aloha.core.navigation.VideoKey
import social.aloha.core.navigation.WatchKey
import social.aloha.core.navigation.YearKey
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.openInBrowser
import social.aloha.core.ui.openLink
import social.aloha.feature.audio.AudioRoute
import social.aloha.feature.audio.MiniPlayer
import social.aloha.feature.composer.ComposerRoute
import social.aloha.feature.composer.DraftsRoute
import social.aloha.feature.composer.ScheduledPostsRoute
import social.aloha.feature.conversations.ConversationsRoute
import social.aloha.feature.conversations.NewMessageRoute
import social.aloha.feature.explore.ExploreRoute
import social.aloha.feature.hashtags.HashtagsRoute
import social.aloha.feature.hashtags.TagGroupRoute
import social.aloha.feature.lists.ListMembersRoute
import social.aloha.feature.lists.ListsRoute
import social.aloha.feature.mediaviewer.MediaViewerRoute
import social.aloha.feature.moderation.ModerationRoute
import social.aloha.feature.moderation.R as ModerationR
import social.aloha.feature.moderation.rememberModerator
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
import social.aloha.feature.safety.AnnouncementsBanner
import social.aloha.feature.safety.AnnouncementsRoute
import social.aloha.feature.safety.BlockedRoute
import social.aloha.feature.safety.FilterEditRoute
import social.aloha.feature.safety.FiltersRoute
import social.aloha.feature.safety.InterestsRoute
import social.aloha.feature.safety.R as SafetyR
import social.aloha.feature.saved.SavedPostsRoute
import social.aloha.feature.search.SearchRoute
import social.aloha.feature.settings.R as SettingsR
import social.aloha.feature.settings.SettingsDestination
import social.aloha.feature.settings.SettingsPlaceholder
import social.aloha.feature.settings.SettingsRoute
import social.aloha.feature.settings.SettingsSectionRoute
import social.aloha.feature.settings.YearRoute
import social.aloha.feature.shorts.ShortsRoute
import social.aloha.feature.stories.StoriesRail
import social.aloha.feature.thread.StatusListRoute
import social.aloha.feature.thread.ThreadNavigation
import social.aloha.feature.thread.ThreadRoute
import social.aloha.feature.timeline.ModesOffer
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
    accountButton: @Composable (AccountLinks) -> Unit = {},
    modes: ModeNavigation = ModeNavigation(),
    timeline: @Composable (
        TimelineFeed,
        StatusNavigation,
        HomeLinks,
        accountButton: @Composable () -> Unit,
    ) -> Unit = { feed, navigation, links, button -> ModeTimeline(feed, navigation, links, button) },
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
    val canOpenWindows by rememberUpdatedState(canOpenWindows())
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

            override fun openStoryComposer() {
                backStack.push(ComposerKey(readerId, draftId = UUID.randomUUID().toString(), story = true))
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

            override val newWindow: ((String?, String?) -> Unit)?
                get() = { statusId: String?, accountId: String? ->
                    WindowActivity.open(context, readerId, statusId, accountId)
                }.takeIf { canOpenWindows }

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
                        // where the account button's sheet leads, the same from every top-level screen
                        val accountLinks = AccountLinks { backStack.push(it.key(readerId, serverAccountId)) }
                        // home and each mode: the same account button, each timeline of its own
                        val modeEntry = @Composable { feed: TimelineFeed ->
                            val links = HomeLinks(
                                onSearch = { backStack.push(SearchKey(readerId)) },
                                onAnnouncements = { backStack.push(AnnouncementsKey(readerId)) },
                            )
                            timeline(feed, statusNavigation, links) { accountButton(accountLinks) }
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
                                navigationIcon = { accountButton(accountLinks) },
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
                        entry<ListsKey> { key ->
                            ListsRoute(
                                key,
                                onOpen = { backStack.push(ListKey(key.readerId, it.id, it.title)) },
                                onBack = { backStack.remove(key) },
                            )
                        }
                        entry<ListKey> { key ->
                            TimelineRoute(
                                statusNavigation,
                                feed = TimelineFeed.List(key.listId, key.title),
                                navigationIcon = {
                                    IconButton(onClick = { backStack.remove(key) }) {
                                        Icon(AlohaIcons.Back, stringResource(R.string.list_back))
                                    }
                                },
                                toolbar = {
                                    IconButton(onClick = {
                                        backStack.push(ListMembersKey(key.readerId, key.listId, key.title))
                                    }) { Icon(AlohaIcons.Members, stringResource(R.string.list_members)) }
                                },
                            )
                        }
                        entry<ListMembersKey> { key ->
                            ListMembersRoute(
                                key,
                                onProfile = { statusNavigation.openProfile(it, null) },
                                onBack = { backStack.remove(key) },
                            )
                        }
                        entry<HashtagsKey> { key ->
                            HashtagsRoute(
                                key,
                                onTag = statusNavigation::openTag,
                                onGroup = { backStack.push(TagGroupKey(key.readerId, it.name)) },
                                onBack = { backStack.remove(key) },
                            )
                        }
                        entry<InterestsKey> { key -> InterestsRoute(key, onBack = { backStack.remove(key) }) }
                        entry<AnnouncementsKey> { key ->
                            AnnouncementsRoute(
                                key,
                                onLink = { statusNavigation.openLink(it) },
                                onBack = { backStack.remove(key) },
                            )
                        }
                        entry<BlockedKey> { key -> BlockedRoute(key, onBack = { backStack.remove(key) }) }
                        entry<ModerationKey> { key -> ModerationRoute(key, onBack = { backStack.remove(key) }) }
                        entry<YearKey> { key ->
                            YearRoute(onOpenPost = statusNavigation::openThread, onBack = { backStack.remove(key) })
                        }
                        entry<FiltersKey> { key ->
                            FiltersRoute(
                                key,
                                onEdit = { backStack.push(FilterEditKey(key.readerId, it)) },
                                onBack = { backStack.remove(key) },
                            )
                        }
                        entry<FilterEditKey> { key -> FilterEditRoute(key, onDone = { backStack.remove(key) }) }
                        entry<SavedPostsKey> { key ->
                            SavedPostsRoute(key, statusNavigation, onBack = { backStack.remove(key) })
                        }
                        entry<ConversationsKey> { key ->
                            ConversationsRoute(
                                key,
                                onThread = statusNavigation::openThread,
                                onNew = { backStack.push(NewMessageKey(key.readerId)) },
                                onBack = { backStack.remove(key) },
                            )
                        }
                        entry<NewMessageKey> { key ->
                            NewMessageRoute(
                                key,
                                onPick = { acct ->
                                    // the composer takes this screen's place: back from it is the conversations
                                    backStack.remove(key)
                                    backStack.push(
                                        ComposerKey(
                                            key.readerId,
                                            draftId = UUID.randomUUID().toString(),
                                            sharedText = "@$acct ",
                                            direct = true,
                                        ),
                                    )
                                },
                                onBack = { backStack.remove(key) },
                            )
                        }
                        entry<TagGroupKey> { key ->
                            TagGroupRoute(key, statusNavigation, onBack = { backStack.remove(key) })
                        }
                        entry<SearchKey> { key ->
                            SearchRoute(key, statusNavigation, onBack = { backStack.remove(key) }) {
                                ExploreRoute(key.readerId, statusNavigation)
                            }
                        }
                        entry<SettingsKey>(
                            metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                                SettingsPlaceholder()
                            }),
                        ) {
                            val moderator = rememberModerator(readerId)
                            SettingsRoute(
                                onBack = { backStack.removeLastOrNull() },
                                onSection = { backStack.push(SettingsSectionKey(it)) },
                                destinations = listOf(
                                    // where the account sheet leads too, so Settings is complete on its own
                                    SettingsDestination(
                                        "filters",
                                        FILTERS_ORDER,
                                        SettingsR.string.settings_filters,
                                        AlohaIcons.Filtered,
                                    ) {
                                        backStack.push(FiltersKey(readerId))
                                    },
                                    SettingsDestination(
                                        "blocked",
                                        BLOCKED_ORDER,
                                        SafetyR.string.blocked_title,
                                        AlohaIcons.Report,
                                    ) {
                                        backStack.push(BlockedKey(readerId))
                                    },
                                    SettingsDestination(
                                        "year",
                                        YEAR_ORDER,
                                        SettingsR.string.year_title,
                                        AlohaIcons.Recent,
                                    ) {
                                        backStack.push(YearKey(readerId))
                                    },
                                ) + listOfNotNull(
                                    SettingsDestination(
                                        "moderation",
                                        MODERATION_ORDER,
                                        ModerationR.string.moderation_title,
                                        AlohaIcons.Report,
                                    ) {
                                        backStack.push(ModerationKey(readerId))
                                    }.takeIf { moderator },
                                ),
                            )
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
private fun ModeTimeline(
    feed: TimelineFeed,
    navigation: StatusNavigation,
    links: HomeLinks,
    accountButton: @Composable () -> Unit,
) {
    // Shorts is a pager of its own rather than a timeline of rows
    if (feed.mode == FeedMode.Shorts) return ShortsRoute(navigation, accountButton)
    // and Audio a list to play from
    if (feed.mode == FeedMode.Audio) return AudioRoute(navigation, accountButton)
    TimelineRoute(
        navigation,
        feed = feed,
        navigationIcon = accountButton,
        // search is reached from Home
        onSearch = links.onSearch.takeIf { feed == TimelineFeed.Home },
        header = {
            when (feed.mode) {
                FeedMode.Photos -> StoriesRail(
                    onProfile = { navigation.openProfile(it, null) },
                    onNewStory = navigation::openStoryComposer,
                )

                FeedMode.Video -> ContinueWatching(onOpen = navigation::openVideo)

                // what the server announces is said on Home, the timeline every reader opens
                else -> if (feed == TimelineFeed.Home) AnnouncementsBanner(onOpen = links.onAnnouncements)
            }
        },
    )
    // the optional modes are offered once, on the timeline every reader opens first
    if (feed == TimelineFeed.Home) ModesOffer()
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
        AlohaApp("preview", "1", timeline = { _, _, _, _ -> Placeholder(stringResource(R.string.destination_home)) })
    }
}

/** Where Home leads beyond its timeline: search, from its toolbar, and the server's announcements. */
data class HomeLinks(val onSearch: () -> Unit = {}, val onAnnouncements: () -> Unit = {})

/** Where the account button's sheet leads, one [AccountPlace] at a time. */
data class AccountLinks(val open: (AccountPlace) -> Unit = {})

/** Where the account sheet leads. */
enum class AccountPlace {
    Profile,
    Messages,
    Bookmarks,
    Favourites,
    Archived,
    Lists,
    Hashtags,
    Interests,
    Filters,
    Announcements,
    Settings,
}

private fun AccountPlace.key(readerId: String, serverAccountId: String): NavKey = when (this) {
    AccountPlace.Profile -> AccountKey(readerId, id = serverAccountId)
    AccountPlace.Messages -> ConversationsKey(readerId)
    AccountPlace.Bookmarks -> SavedPostsKey(readerId, SavedKind.Bookmarks)
    AccountPlace.Favourites -> SavedPostsKey(readerId, SavedKind.Favourites)
    AccountPlace.Archived -> SavedPostsKey(readerId, SavedKind.Archived)
    AccountPlace.Lists -> ListsKey(readerId)
    AccountPlace.Hashtags -> HashtagsKey(readerId)
    AccountPlace.Interests -> InterestsKey(readerId)
    AccountPlace.Filters -> FiltersKey(readerId)
    AccountPlace.Announcements -> AnnouncementsKey(readerId)
    AccountPlace.Settings -> SettingsKey
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
    val shown = LocalReadingStyle.current.unreadBadge
    if (shown && key == NotificationsKey && unreadNotifications > 0) Badge { Text(badgeCount(unreadNotifications)) }
}

/** Whether a thread or a profile can open in a window of its own: where there is room, or beside another app. */
@Composable
private fun canOpenWindows(): Boolean {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    return wide || LocalActivity.current?.isInMultiWindowMode == true
}

// after Writing in the settings list, before Notifications
private const val FILTERS_ORDER = 250

// near the end of the list, before About this server
private const val YEAR_ORDER = 970

// after Sound and haptics, among what keeps the reader safe
private const val BLOCKED_ORDER = 380

/** Right after the reader's own blocks: the server's moderation, for its moderators. */
private const val MODERATION_ORDER = 390
