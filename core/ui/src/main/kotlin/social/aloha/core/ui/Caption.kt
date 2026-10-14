// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints

/**
 * A picture's words, as a photo app shows them: [CAPTION_LINES] lines, the last ending in "… more" where there is
 * more, which a tap opens in place, ending in "less" to shorten it again. A screen reader reads them whole with
 * the card.
 */
@Composable
internal fun Caption(row: StatusRowUi, style: TextStyle, emoji: Map<String, InlineTextContent>) {
    var expanded by rememberSaveable(row.rowId) { mutableStateOf(false) }
    // the colour links have, so "more" and "less" read as something to tap
    val colour = MaterialTheme.colorScheme.primary
    if (expanded) {
        val less = stringResource(R.string.status_less)
        val whole = remember(row.body, less, colour) { row.body + captionLink(" ", less, colour) { expanded = false } }
        return Text(whole, inlineContent = emoji, style = style)
    }
    val more = stringResource(R.string.status_more)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val width = constraints.maxWidth
        val cut = remember(row.body, style, width, more, colour) {
            measurer.shortened(row.body, captionLink("\u2026 ", more, colour) { expanded = true }, style, width)
        }
        Text(cut ?: row.body, inlineContent = emoji, style = style, maxLines = CAPTION_LINES)
    }
}

/** [lead], then [label] as a link in [colour] that does [onClick]. */
private fun captionLink(lead: String, label: String, colour: Color, onClick: () -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(lead)
        withLink(LinkAnnotation.Clickable(label, TextLinkStyles(SpanStyle(color = colour))) { onClick() }) {
            append(label)
        }
    }

/**
 * [text] cut so that it and [tail] fit in [CAPTION_LINES] lines of [width], or null where the whole text fits.
 * Emoji are measured by their names, which are wider than the pictures drawn: the cut errs short, never over.
 */
private fun TextMeasurer.shortened(
    text: AnnotatedString,
    tail: AnnotatedString,
    style: TextStyle,
    width: Int,
): AnnotatedString? {
    val fits = { candidate: AnnotatedString ->
        !measure(candidate, style, maxLines = CAPTION_LINES, constraints = Constraints(maxWidth = width))
            .hasVisualOverflow
    }
    if (fits(text)) return null
    // the longest start of the text that still leaves room for the tail, found by halving
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(text.subSequence(0, mid) + tail)) low = mid else high = mid - 1
    }
    return text.subSequence(0, low).trimEnd() + tail
}

private fun AnnotatedString.trimEnd(): AnnotatedString = subSequence(0, text.trimEnd().length)

private const val CAPTION_LINES = 2
