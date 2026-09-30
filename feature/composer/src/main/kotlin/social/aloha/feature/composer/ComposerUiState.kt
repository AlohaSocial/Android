// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.Immutable
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility

/** One account the post can go out as, for the picker at the top. */
@Immutable
internal data class Author(val id: String, val handle: String, val name: String, val avatarUrl: String?)

/** The post being answered, as the collapsible line above the text shows it. */
@Immutable
internal data class ReplyContext(val author: String, val excerpt: String)

/** A completion offered for the word at the cursor. */
@Immutable
internal data class Suggestion(val replacement: String, val label: String, val imageUrl: String?)

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
    /** Characters left in each segment of the thread, negative when over. */
    val remaining: List<Int> = listOf(0),
    val games: List<ComposerGames.Kind> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
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
    /** Whether to warn before posting pictures without a description. */
    val warnMissingDescription: Boolean = true,
    /** Whether a short gets `#shorts`; null until the writer is asked, once. */
    val tagShorts: Boolean? = null,
    /** How many segments of the thread are already posted; a retry starts after them. */
    val posted: Int = 0,
    val posting: Boolean = false,
    val failure: PostFailure? = null,
    val done: Boolean = false,
) {
    val canPost: Boolean
        get() = ready && author != null && !posting && remaining.all { it >= 0 } && uploaded &&
            poll?.ready(maxPollOptionCharacters) != false

    /** Every attachment is on the server, ready to be attached. */
    val uploaded: Boolean get() = attachments.flatten().all { it.mediaId != null }

    /** Some attachment has no description, which a screen reader then cannot describe. */
    val undescribed: Boolean get() = attachments.flatten().any { it.description.isBlank() }
}

/** The visibilities a writer picks between; an unknown one is never offered. */
internal val Visibility.Companion.choices: List<Visibility>
    get() = listOf(Visibility.Public, Visibility.Unlisted, Visibility.Private, Visibility.Direct)
