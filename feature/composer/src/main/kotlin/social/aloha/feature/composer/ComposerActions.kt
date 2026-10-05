// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.ui.text.input.TextFieldValue
import java.time.Instant
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility

/** What the composer's controls do; the screen itself decides nothing. */
internal interface ComposerActions :
    MediaActions,
    LaterActions,
    QuoteActions,
    TypingActions {
    fun onClose()

    fun onPost()

    fun onText(index: Int, value: TextFieldValue)

    fun onSpoiler(shown: Boolean, text: String)

    fun onVisibility(visibility: Visibility)

    fun onLanguage(language: String?)

    fun onQuotePolicy(policy: QuotePolicy)

    fun onAddSegment()

    fun onRemoveSegment(index: Int)

    fun onAuthor(id: String)

    /** Opens draft [id] in place of the empty post. */
    fun onResume(id: String)

    /** Takes [handle] out of the people a reply is addressed to. */
    fun onLeaveOut(handle: String)

    /** Shares the post as a story instead, or back as a post. */
    fun onStory(on: Boolean)

    /** How long the story's picture or card shows for. */
    fun onStorySeconds(seconds: Int)
}

/** What helps with the words: completing the one at the cursor, and putting in emoji. */
internal interface TypingActions {
    fun onSuggestion(suggestion: Suggestion)

    /** Opens search for [query], where no account the server knows matches it. */
    fun onFindPeople(query: String)

    fun onEmoji(emoji: CustomEmoji)
}

/** What the controls for later do: scheduling, and the posts kept for later. */
internal interface LaterActions {
    /** Opens the day and time picker for when the post goes out. */
    fun onPickSchedule()

    /** Posts at [at], or at once when null. */
    fun onSchedule(at: Instant?)

    /** Opens the list of posts waiting on the server for their time. */
    fun onScheduledPosts()

    /** Opens the drafts. */
    fun onDrafts()
}

/** What the attachment controls do. */
internal interface MediaActions {
    /** Opens the picture and video picker for the post being written. */
    fun onPickMedia()

    /** Opens the file picker for the post being written. */
    fun onPickFiles()

    /** Opens the camera for a photo, a video or a short. */
    fun onCapture(capture: Capture)

    /** Opens the server's GIF library. */
    fun onGifs()

    /** Asks for the path of a file in the writer's Nextcloud. */
    fun onNextcloudFile()

    /** Attaches the pictures and videos on the clipboard. */
    fun onPaste()

    /** Sets the opening post's poll, which takes the place of its media; null removes it. */
    fun onPoll(poll: PollUi?)

    fun onEditMedia(id: String)

    fun onRemoveMedia(id: String)

    /** The attachments of one post, in the order they go out in. */
    fun onOrderMedia(ids: List<String>)

    fun onRetryMedia(id: String)

    fun onSensitive(sensitive: Boolean)

    /** Turns the short post into a card, or back into text. */
    fun onCard(on: Boolean)

    fun onCardBackground(index: Int)
}
