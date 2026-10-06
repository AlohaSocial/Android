// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.Immutable
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility
import social.aloha.core.sync.UploadState

/** One account the post can go out as, for the picker at the top. */
@Immutable
internal data class Author(val id: String, val handle: String, val name: String, val avatarUrl: String?)

/** The post being answered, as the collapsible line above the text shows it. */
@Immutable
internal data class ReplyContext(val author: String, val excerpt: String)

/** The post a new one quotes: who wrote it, how it begins, who may see it, and where it lives. */
@Immutable
internal data class QuoteUi(
    val statusId: String,
    val author: String,
    val excerpt: String,
    val url: String,
    val visibility: Visibility,
    val own: Boolean,
)

/** What the composer says once about a quote: what quoting a quiet public post does, or why it is a link. */
internal enum class QuoteNotice { Unlisted, Linked }

/** A completion offered for the word at the cursor. */
@Immutable
internal data class Suggestion(val replacement: String, val label: String, val imageUrl: String?)

/** What is offered for the word at the cursor: nothing while [kind] is null, [loading] while the server is asked. */
@Immutable
internal data class CompletionsUi(
    val kind: CompletionKind? = null,
    val query: String = "",
    val items: List<Suggestion> = emptyList(),
    val loading: Boolean = false,
) {
    /** Whether the strip shows: something offered, being looked for, or a way on from there. */
    fun shown(hasEmojis: Boolean): Boolean = when (kind) {
        null -> false
        CompletionKind.Account -> true
        CompletionKind.Emoji -> loading || items.isNotEmpty() || hasEmojis
        CompletionKind.Hashtag -> loading || items.isNotEmpty()
    }
}

/** The draft a new, empty composer offers to go back to: its id, and how it begins. */
@Immutable
internal data class ResumeUi(val draftId: String, val excerpt: String)

/** Who may quote the post, where the server lets its writer choose. */
internal enum class QuotePolicy(val wire: String?) { Anyone(null), Followers("followers"), Nobody("nobody") }

/** Why posting stopped. */
@Immutable
internal sealed interface PostFailure {
    /** The server refused the post and said why; the text stays as it was for the writer to fix. */
    data class Refused(val message: String?) : PostFailure

    /** The server could not be reached or failed; posting again resumes where it stopped. */
    data class Unreached(val trouble: Trouble) : PostFailure

    /** The post being answered cannot be found from the account chosen. */
    data object ReplyNotFound : PostFailure

    /** The card could not be drawn or uploaded; nothing was posted rather than plain text instead. */
    data object CardFailed : PostFailure
}

/** A poll as the writer builds it; it goes out with the opening post, never together with media. */
@Immutable
internal data class PollUi(
    val options: List<String> = listOf("", ""),
    val seconds: Long = DAY_SECONDS,
    val multiple: Boolean = false,
    val hideTotals: Boolean = false,
) {
    /** The choices that go out: filled in, trimmed. */
    val choices: List<String> get() = options.map(String::trim).filter(String::isNotEmpty)

    /** At least two different choices, none longer than [maxCharacters]. */
    fun ready(maxCharacters: Int): Boolean =
        choices.size >= 2 && choices.distinct().size == choices.size && choices.all { it.length <= maxCharacters }

    companion object {
        const val DAY_SECONDS = 86_400L
        private val LENGTHS = listOf(300L, 1_800L, 3_600L, 21_600L, 43_200L, DAY_SECONDS, 259_200L, 604_800L)

        /** The lengths a poll may run for on a server that allows [min] to [max] seconds. */
        fun durations(min: Long, max: Long): List<Long> = LENGTHS.filter { it in min..max }.ifEmpty { listOf(min) }
    }
}

@Immutable
internal data class ComposerUiState(
    /** The account, the post being answered and the writer's defaults are in place. */
    val ready: Boolean = false,
    val author: Author? = null,
    val authors: List<Author> = emptyList(),
    val reply: ReplyContext? = null,
    val visibility: Visibility = Visibility.Public,
    val visibilities: List<Visibility> = Visibility.choices,
    /** Why some visibilities are missing: a reply may not reach further than the post it answers. */
    val visibilityClamped: Boolean = false,
    val language: String? = null,
    val spoilerShown: Boolean = false,
    val quotePolicies: List<QuotePolicy> = emptyList(),
    val quotePolicy: QuotePolicy = QuotePolicy.Anyone,
    val quote: QuoteUi? = null,
    val quoteNotice: QuoteNotice? = null,
    /** Posting waits on the writer: someone else's followers-only post is about to be quoted. */
    val confirmQuote: Boolean = false,
    /** The writer said yes to that once, for this post. */
    val quoteConfirmed: Boolean = false,
    /** Characters left in each segment of the thread, negative when over. */
    val remaining: List<Int> = listOf(0),
    /** Where each segment's text goes past the limit; null for one within it. */
    val overFrom: List<Int?> = listOf(null),
    /** The people a reply to several is addressed to, still in its text, each to be left out; else empty. */
    val mentioned: List<String> = emptyList(),
    /** The latest draft, offered while a new post is still empty. */
    val resume: ResumeUi? = null,
    /** The language the opening post reads as, where the device is sure enough of it. */
    val detected: String? = null,
    val games: List<ComposerGames.Kind> = emptyList(),
    val completions: CompletionsUi = CompletionsUi(),
    val emojis: List<CustomEmoji> = emptyList(),
    /** The attachments of each segment of the thread. */
    val attachments: List<List<Attachment>> = listOf(emptyList()),
    /** Whether the media carry a warning of their own, whatever the text says. */
    val mediaSensitive: Boolean = false,
    /** How many attachments one post may carry on this server. */
    val maxAttachments: Int = 4,
    /** The poll of the opening post, when it has one. */
    val poll: PollUi? = null,
    val maxPollOptions: Int = 4,
    val maxPollOptionCharacters: Int = 50,
    val pollDurations: List<Long> = listOf(PollUi.DAY_SECONDS),
    /** When the post goes out, if not now; a thread cannot be scheduled. */
    val scheduledAt: Instant? = null,
    /** Whether the server has a GIF library of its own to attach from. */
    val gifLibrary: Boolean = false,
    /** Whether the server attaches files from the writer's own Nextcloud by path. */
    val nextcloudFiles: Boolean = false,
    val attachFailure: AttachFailure? = null,
    val editFailure: EditFailure? = null,
    val card: CardUi = CardUi(),
    /** Whether the post is short and plain enough to go out as a card. */
    val cardFits: Boolean = false,
    /** Whether the post could go out as a story instead: one picture or video and a short caption. */
    val storyFits: Boolean = false,
    /** The writer chose to share it as a story, which goes once its day is up. */
    val asStory: Boolean = false,
    /** How long the story's picture or card shows for, in seconds. */
    val storySeconds: Int = 5,
    /** Whether to warn before posting pictures without a description. */
    val warnMissingDescription: Boolean = true,
    /** Whether a picture's description can be drafted on the device: Draft alt text is on. */
    val draftsAltText: Boolean = false,
    /** Posting asks first, where the writer chose so. */
    val confirmBeforePosting: Boolean = false,
    /** Each part of a thread ends in its number, counted in its length. */
    val numberThreads: Boolean = false,
    /** The Post button sits beside the count above the keyboard, within thumb reach. */
    val postAtBottom: Boolean = false,
    /** Whether a short gets `#shorts`; null until the writer is asked, once. */
    val tagShorts: Boolean? = null,
    /** How many segments of the thread are already posted; a retry starts after them. */
    val posted: Int = 0,
    val posting: Boolean = false,
    /** Some post of the thread holds nothing to send: no text, no media, no poll. */
    val empty: Boolean = false,
    val failure: PostFailure? = null,
    val done: Boolean = false,
    /** The post went to the outbox, to go out with a network. */
    val queued: Boolean = false,
    /** The writer's own post, out already, is being edited: one post, same visibility, same author. */
    val editing: Boolean = false,
    /** The writer's own post this one writes again, deleted once this one is out. */
    val replaces: String? = null,
) {
    val canPost: Boolean
        get() = ready && author != null && !posting && !empty && remaining.all { it >= 0 } && (uploaded || canWait) &&
            poll?.ready(maxPollOptionCharacters) != false

    /** Another post can join the thread: an edit changes one post, and a scheduled one cannot be answered yet. */
    val threadable: Boolean get() = !posting && scheduledAt == null && !editing

    /** A post of its own, not a reply to one or one written again: what a story can be. */
    val fresh: Boolean get() = !editing && reply == null && replaces == null

    /** The post goes out as a story. */
    val sharesStory: Boolean get() = asStory && storyFits

    /** A story the writer picks the length of: a picture or a card; a clip plays to its end. */
    val storyLengthPicked: Boolean get() = sharesStory && attachments.flatten().none { it.isVideo }

    /** Every attachment is on the server, ready to be attached. */
    val uploaded: Boolean get() = attachments.flatten().all { it.mediaId != null }

    /**
     * What is not on the server waits for a network, or gave up waiting, with its file here: the post
     * can go to the outbox, which uploads it later. One uploading now is waited for instead.
     */
    val canWait: Boolean
        get() = attachments.flatten().all {
            it.mediaId != null ||
                (it.file != null && it.upload in setOf(UploadState.Queued, UploadState.Failed, UploadState.Cancelled))
        }

    /** Some attachment has no description, which a screen reader then cannot describe. */
    val undescribed: Boolean get() = attachments.flatten().any { it.description.isBlank() }
}

/** The visibilities a writer picks between; an unknown one is never offered. */
internal val Visibility.Companion.choices: List<Visibility>
    get() = listOf(Visibility.Public, Visibility.Unlisted, Visibility.Private, Visibility.Direct)
