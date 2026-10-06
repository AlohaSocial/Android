// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.data.thread.ReplyNudge
import social.aloha.core.model.ApplicationSummary
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Reaction
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.ui.StatusRowUi

@Immutable
internal sealed interface ThreadItem {
    val key: String

    /** A post of the conversation; [depth] 0 above and at the focused post, 1 and on for replies. */
    data class Post(val row: StatusRowUi, val focused: Boolean, val depth: Int) : ThreadItem {
        override val key: String get() = row.rowId
    }

    data class More(val parentId: String, val count: Int, val depth: Int) : ThreadItem {
        override val key: String get() = "more-$parentId"
    }
}

/** One earlier version of an edited post, oldest first; [media] holds each attachment's description, null for none. */
@Immutable
internal data class EditVersion(
    val createdAt: Instant,
    val spoiler: String?,
    val body: AnnotatedString,
    val media: List<String?> = emptyList(),
)

/**
 * What the thread screen shows. The focused post draws from the cache while the conversation loads;
 * [gone] means the server no longer has it for this account.
 *
 * @property lists the focused post's lists worth offering, with their counts where the server gives one.
 */
@Immutable
internal data class ThreadUiState(
    val items: List<ThreadItem> = emptyList(),
    val loading: Boolean = true,
    val trouble: Trouble? = null,
    val gone: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val lists: Map<StatusListKind, Int?> = emptyMap(),
    /** Avatars of the first few in a list, for the lists that show who. */
    val people: Map<StatusListKind, List<String?>> = emptyMap(),
    val edited: Boolean = false,
    val history: List<EditVersion>? = null,
    val actionFailed: Boolean = false,
    /** Whether the reader's server takes emoji reactions, which the focused post then offers. */
    val canReact: Boolean = false,
    /** Whether the reader's server keeps albums, which the reader's own posts can go into. */
    val albums: Boolean = false,
    /** Whether the reader's server archives posts, which the reader's own posts can then be. */
    val archive: Boolean = false,
    /** A post was just archived, which the screen confirms. */
    val archived: Boolean = false,
    /** The reader's own avatar, on the reply bar. */
    val readerAvatar: String? = null,
    /** The app the focused post was written with, where the server says. */
    val application: ApplicationSummary? = null,
    /** A pause before a reply, waiting for the reader's answer. */
    val nudge: ReplyNudge? = null,
    /** A reply to open now, the nudge answered or never needed. */
    val replyTo: String? = null,
    /** Replies a refresh found, held back until the reader asks for them, so the list stays put. */
    val pendingReplies: Int = 0,
)

/** What a pushed list of the focused post shows. */
@Immutable
internal sealed interface StatusListState {
    data object Loading : StatusListState

    data class Failed(val trouble: Trouble) : StatusListState

    /** An account with the custom emoji its name uses. */
    data class Person(val author: StatusRowUi.AuthorUi, val emojis: List<CustomEmoji>)

    data class Accounts(val people: List<Person>) : StatusListState

    data class Posts(val rows: List<StatusRowUi>, val now: Instant) : StatusListState

    data class Reactions(val reactions: List<Reaction>) : StatusListState
}

/** What the thread screen asks for beyond a row's own actions. */
internal interface ThreadScreenActions {
    fun onBack()

    fun onRefresh()

    fun onMore(parentId: String)

    fun onList(kind: StatusListKind)

    fun onHistory()

    fun onHistoryDismissed()
}
