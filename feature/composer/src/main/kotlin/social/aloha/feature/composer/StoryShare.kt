// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import social.aloha.core.data.Answer
import social.aloha.core.data.Trouble
import social.aloha.core.data.stories.Stories
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Story

/**
 * A post shared as a story instead, gone after a day: one picture or video with the text as its
 * caption, or the text alone drawn as a card. A picture or a card shows for as long as the writer
 * picks; a clip plays to its end. A story never waits in the outbox, since one sent late would be
 * mostly gone; without a network it fails like any other post.
 */
internal class StoryShare(
    private val stories: Stories,
    private val attachments: Attachments,
    private val cards: TextCards,
    chosen: Boolean,
) {
    private val choice = MutableStateFlow(chosen)
    private val length = MutableStateFlow(Story.DEFAULT_DURATION.toInt())

    /** Whether the writer chose to share the post as a story. */
    val chosen: StateFlow<Boolean> = choice.asStateFlow()

    /** How long a picture or a card shows for, in seconds. */
    val seconds: StateFlow<Int> = length.asStateFlow()

    fun onStory(on: Boolean) {
        choice.value = on
    }

    fun onSeconds(seconds: Int) {
        length.value = seconds
    }

    /**
     * Shares what [state] holds, a card drawn from [text] first where it is one, with [text] as its
     * caption, which is also how a screen reader reads a card; [onShared] once it is out.
     */
    suspend fun share(
        account: SignedInAccount,
        state: ComposerUiState,
        text: String,
        onShared: () -> Unit,
    ): PostFailure? {
        if (!cards.ready(state.cardFits, text)) return PostFailure.CardFailed
        if (!attachments.sync(account)) return PostFailure.Unreached(Trouble.Offline)
        val medium = attachments.byPost.value.flatten().single()
        val seconds = if (medium.isVideo) CLIP_SECONDS else length.value
        val answer = stories.post(account, checkNotNull(medium.mediaId), text.trim().ifEmpty { null }, seconds)
        if (answer is Answer.Got) onShared()
        return (answer as? Answer.Missed)?.error?.let(::failureOf)
    }

    companion object {
        /** The longest a story shows for; the server clamps to it too. */
        private const val CLIP_SECONDS = 30

        /** The most a story's caption holds. */
        private const val CAPTION = 500

        /** How long a picture or a card may show for, to pick from; the server takes 3 to 30. */
        val LENGTHS: List<Int> = listOf(5, 10, 15)

        /**
         * Whether the post could be a story: one post, [alone] (new, with no poll or later time) and
         * with a short caption, holding either one picture or video, or nothing but its text drawn as a
         * [card].
         */
        fun fits(segments: List<String>, media: List<Attachment>, alone: Boolean, card: Boolean): Boolean {
            val shape = if (card) media.isEmpty() else media.singleOrNull()?.let { it.isPicture || it.isVideo } == true
            return alone && shape && segments.size == 1 && segments[0].length <= CAPTION
        }
    }
}
