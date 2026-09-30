// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.Story
import social.aloha.core.ui.StatusRowUi

/** The profile's tabs; the last two only where the server has them. */
internal enum class ProfileTab { Posts, Replies, Media, Videos, Collections, Stories }

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
) {
    @Immutable
    data class Field(val name: AnnotatedString, val value: AnnotatedString, val verified: Boolean)
}

/** How the reader relates to the account, as far as the controls care. */
@Immutable
internal data class Relation(
    val following: Boolean = false,
    val requested: Boolean = false,
    val followedBy: Boolean = false,
    val muting: Boolean = false,
    val blocking: Boolean = false,
    val blockedBy: Boolean = false,
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
)

/** What changes the profile: its tabs and pages, and how the reader relates to the account. */
internal interface ProfileActions {
    fun onRefresh()

    fun onTab(tab: ProfileTab)

    fun onNearEnd()

    fun onFillGap(gapId: String)

    fun onFollow()

    fun onUnfollow()

    fun onMute(mute: Boolean)

    fun onBlock(block: Boolean)
}

/** What the profile screen asks for: [ProfileActions], and the places it leads. */
internal interface ProfileScreenActions : ProfileActions {
    fun onBack()

    fun onPeople(followers: Boolean)

    fun onOpenInBrowser(url: String)
}
