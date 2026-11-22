// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.navigation.OwnProfilePlace
import social.aloha.core.ui.ListSection
import social.aloha.core.ui.StatusNavigation
import social.aloha.feature.composer.draftsSection
import social.aloha.feature.composer.scheduledSection
import social.aloha.feature.profile.OwnProfileTabs
import social.aloha.feature.profile.StoriesPlayer
import social.aloha.feature.saved.archivedSection
import social.aloha.feature.saved.savedSection
import social.aloha.feature.settings.statisticsSection
import social.aloha.feature.stories.AccountStoriesPlayer

/**
 * The tabs only [readerId]'s own profile has, from the features that keep them, as the web app has
 * them: Saved, Drafts with the archive where the server keeps one ([archive]), and Statistics. A draft
 * opens through [onDraft]; [onConnect] connects the Nextcloud Statistics needs.
 */
internal class AppOwnProfileTabs(
    private val readerId: String,
    private val archive: Boolean,
    private val navigation: StatusNavigation,
    private val onDraft: (String) -> Unit,
    private val onConnect: () -> Unit,
) : OwnProfileTabs {
    @Composable
    override fun saved(bookmarks: Boolean, snackbars: SnackbarHostState): ListSection =
        savedSection(readerId, bookmarks, navigation, snackbars)

    @Composable
    override fun drafts(snackbars: SnackbarHostState): ListSection {
        val drafts = draftsSection(readerId, onDraft)
        val scheduled = scheduledSection(readerId, snackbars)
        val archived = if (archive) archivedSection(readerId, navigation, snackbars) else null
        return ListSection {
            drafts.addTo(this)
            scheduled.addTo(this)
            archived?.addTo(this)
        }
    }

    @Composable
    override fun statistics(snackbars: SnackbarHostState): ListSection =
        statisticsSection(readerId, onConnect, snackbars)
}

/** A profile's avatar plays the account's shorts as the stories rail does. */
internal object AppStoriesPlayer : StoriesPlayer {
    @Composable
    override fun Play(reel: StoryReel, onProfile: (accountId: String) -> Unit, onClose: () -> Unit) =
        AccountStoriesPlayer(reel, onProfile, onClose)
}

/** The reader's own profile, as [serverAccountId] on their server, opened on [place]. */
internal fun ownProfile(readerId: String, serverAccountId: String, place: OwnProfilePlace? = null): AccountKey =
    AccountKey(readerId, id = serverAccountId, open = place)

/**
 * Opens [readerId]'s draft [draftId] in the composer; one picked from the drafts the composer or a
 * notification opened ([DraftsKey]) takes their place, and that of the composer they were opened from.
 */
internal fun MutableList<NavKey>.openDraft(readerId: String, draftId: String) {
    if (lastOrNull() is DraftsKey) {
        removeLastOrNull()
        if (lastOrNull() is ComposerKey) removeLastOrNull()
    }
    push(ComposerKey(readerId, draftId = draftId))
}

/** Where the drafts the composer, a notification or a shortcut asks for are: the reader's own Drafts tab. */
internal fun DraftsKey.asOwnProfile(serverAccountId: String): AccountKey =
    ownProfile(readerId, serverAccountId, OwnProfilePlace.Drafts)
