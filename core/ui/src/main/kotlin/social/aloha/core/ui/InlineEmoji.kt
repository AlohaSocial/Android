// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.provider.Settings
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import social.aloha.core.model.CustomEmoji

/**
 * The inline slots a rendered status body names, one per custom emoji, each a square the height of the
 * text. The image itself says nothing to a screen reader: the slot's alternate text, `:shortcode:`, is
 * what is read. With [animate] off, or when the system asks to remove animations, the still image is drawn.
 */
@Composable
public fun rememberEmojiContent(
    emojis: List<CustomEmoji>,
    animate: Boolean,
    size: TextUnit = 1.2.em,
): Map<String, InlineTextContent> {
    val moving = animate && !rememberReducedMotion()
    return remember(emojis, moving, size) { emojiSlots(emojis, moving, size) }
}

private fun emojiSlots(emojis: List<CustomEmoji>, animate: Boolean, size: TextUnit): Map<String, InlineTextContent> =
    emojis.associate { emoji ->
        emojiSlot(emoji.shortcode) to
            InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage(
                    model = if (animate) emoji.url else emoji.stillUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
    }
