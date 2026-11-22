// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.ui.ListSection

/**
 * What the tabs only the reader's own profile has show, drawn by the features that keep it: the profile
 * lays each one out under its header and tabs, in its one list. Each says what it has to say through
 * the profile's [SnackbarHostState].
 */
@Stable
public interface OwnProfileTabs {
    /** Saved: the posts the reader liked or bookmarked, on the bookmarks first when [bookmarks]. */
    @Composable
    public fun saved(bookmarks: Boolean, snackbars: SnackbarHostState): ListSection

    /** Drafts: the reader's drafts, scheduled posts and, where the server keeps them, archived posts. */
    @Composable
    public fun drafts(snackbars: SnackbarHostState): ListSection

    /** Statistics: how the reader's posts are doing, where the server counts them. */
    @Composable
    public fun statistics(snackbars: SnackbarHostState): ListSection
}

/** [tab]'s part from [tabs], for one of the reader's own tabs; null for the others, or with nothing to draw them. */
@Composable
internal fun ownSection(
    tab: ProfileTab,
    tabs: OwnProfileTabs?,
    bookmarks: Boolean,
    snackbars: SnackbarHostState,
): ListSection? = when {
    tabs == null -> null
    tab == ProfileTab.Saved -> tabs.saved(bookmarks, snackbars)
    tab == ProfileTab.Drafts -> tabs.drafts(snackbars)
    tab == ProfileTab.Statistics -> tabs.statistics(snackbars)
    else -> null
}

/** The profile as drawn: without the reader's own tabs where nothing draws them ([tabs] null). */
internal fun ProfileUiState.drawing(tabs: OwnProfileTabs?): ProfileUiState =
    if (tabs == null) copy(tabs = this.tabs - OWN_TABS) else this

/**
 * Plays an account's shorts that last a day, from its profile's avatar, as the feature that keeps
 * stories plays them; the app supplies it.
 */
@Stable
public interface StoriesPlayer {
    /** Plays [reel] over the screen; [onProfile] leaves for a profile, [onClose] ends the playing. */
    @Composable
    public fun Play(reel: StoryReel, onProfile: (accountId: String) -> Unit, onClose: () -> Unit)
}
