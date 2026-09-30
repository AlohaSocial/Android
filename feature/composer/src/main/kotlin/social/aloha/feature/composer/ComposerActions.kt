// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.ui.text.input.TextFieldValue
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility

/** What the composer's controls do; the screen itself decides nothing. */
internal interface ComposerActions {
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
