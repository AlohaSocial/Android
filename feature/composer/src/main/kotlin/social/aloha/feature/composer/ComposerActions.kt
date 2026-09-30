// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.ui.text.input.TextFieldValue
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility

/** What the composer's controls do; the screen itself decides nothing. */
internal interface ComposerActions : MediaActions {
    fun onClose()

    fun onPost()

    fun onText(index: Int, value: TextFieldValue)

    fun onSpoiler(shown: Boolean, text: String)

    fun onVisibility(visibility: Visibility)

    fun onLanguage(language: String?)

    fun onQuotePolicy(policy: QuotePolicy)

    fun onSuggestion(suggestion: Suggestion)

    fun onEmoji(emoji: CustomEmoji)

    fun onAddSegment()

    fun onRemoveSegment(index: Int)

    fun onAuthor(id: String)
}

/** What the attachment controls do. */
internal interface MediaActions {
    /** Opens the picture and video picker for the post being written. */
    fun onPickMedia()

    /** Opens the file picker for the post being written. */
    fun onPickFiles()

    fun onEditMedia(id: String)

    fun onRemoveMedia(id: String)

    fun onRetryMedia(id: String)

    fun onSensitive(sensitive: Boolean)
}
