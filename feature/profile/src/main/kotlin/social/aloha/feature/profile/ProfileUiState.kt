// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.data.profile.ListChoice
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.Story
import social.aloha.core.ui.StatusRowUi

/** The profile's tabs; Featured only where the account features something, the last two where the server has them. */
internal enum class ProfileTab { Posts, Replies, Media, Featured, Videos, Collections, Stories }

/** What an account features at the top of its profile: pinned posts and hashtags. */
@Immutable
internal data class FeaturedUi(val posts: List<StatusRowUi>, val tags: List<FeaturedTag>)

/** What the header shows: who, what they say about themselves, and how many. */
@Immutable
internal data class ProfileHeader(
    val author: StatusRowUi.AuthorUi,
    val emojis: List<CustomEmoji>,
    val headerUrl: String?,
    val note: AnnotatedString,
    val fields: List<Field>,
    val posts: Int,
    val following: Int,
    val followers: Int,
    val locked: Boolean,
    val url: String?,
    val isSelf: Boolean,
    /** The account's server when it is not the reader's own, which can then be blocked as a whole. */
    val domain: String?,
    val joined: Instant? = null,
    /** Where the account moved to, when it did. */
    val movedTo: Moved? = null,
    val memorial: Boolean = false,
) {
    @Immutable
    data class Moved(val id: String, val handle: String)

    @Immutable
    data class Field(val name: AnnotatedString, val value: AnnotatedString, val verified: Boolean)
}

/** One of the people the reader follows who also follow the account. */
@Immutable
internal data class Familiar(val id: String, val name: String, val avatarUrl: String?)

/** What the profile asks about before it changes how the reader relates to the account. */
internal enum class Asking { Block, Mute, BlockDomain, RemoveFollower, Lists }

/** How the reader relates to the account, as far as the controls care. */
@Immutable
internal data class Relation(
    val following: Boolean = false,
    val requested: Boolean = false,
    val followedBy: Boolean = false,
    val muting: Boolean = false,
    val blocking: Boolean = false,
    val blockedBy: Boolean = false,
    val showingReblogs: Boolean = true,
    val notifying: Boolean = false,
    val domainBlocking: Boolean = false,
    /** The reader's own note about the account. */
    val note: String? = null,
)

@Immutable
internal sealed interface ProfileItem {
    val key: String

    data class Post(val row: StatusRowUi) : ProfileItem {
        override val key: String get() = row.rowId
    }

    data class Gap(val id: String, val loading: Boolean) : ProfileItem {
        override val key: String get() = id
    }
}

@Immutable
internal data class ProfileUiState(
    val header: ProfileHeader? = null,
    val relation: Relation? = null,
    val highlights: ProfileHighlights? = null,
    val tabs: List<ProfileTab> = listOf(ProfileTab.Posts, ProfileTab.Replies, ProfileTab.Media, ProfileTab.Videos),
    val tab: ProfileTab = ProfileTab.Posts,
    val items: List<ProfileItem> = emptyList(),
    val collections: List<MediaCollection>? = null,
    val stories: List<Story>? = null,
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val changing: Boolean = false,
    val trouble: Trouble? = null,
    val gone: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val actionFailed: Boolean = false,
    /** The reader's lists, once asked for, each saying whether the account is on it. */
    val lists: List<ListChoice>? = null,
    /** The people the reader follows who follow this account too. */
    val familiar: List<Familiar> = emptyList(),
    /** The handle the profile was opened by, shown while the account loads. */
    val knownHandle: String? = null,
    val featured: FeaturedUi? = null,
)

/** What changes the profile: its tabs and pages, and how the reader relates to the account. */
internal interface ProfileActions {
    fun onRefresh()

    fun onTab(tab: ProfileTab)

    fun onNearEnd()

    fun onFillGap(gapId: String)

    /** Changes how the reader relates to the account: follow, mute, block, note and the rest. */
    fun onChange(change: RelationshipChange)

    /** Blocks the account's whole server, or unblocks it. */
    fun onBlockDomain(block: Boolean)

    /** Loads the reader's lists, for putting the account on them. */
    fun onLists()

    fun onListed(listId: String, add: Boolean)
}

/** What the profile screen asks for: [ProfileActions], and the places it leads. */
internal interface ProfileScreenActions : ProfileActions {
    /** Whether the profile can open in a window of its own, which its menu then offers. */
    val windows: Boolean get() = false

    fun onBack()

    fun onNewWindow() {}

    fun onPeople(followers: Boolean)

    fun onOpenInBrowser(url: String)

    fun onAlbum(album: MediaCollection)

    fun onEditProfile()

    fun onReport()
}
