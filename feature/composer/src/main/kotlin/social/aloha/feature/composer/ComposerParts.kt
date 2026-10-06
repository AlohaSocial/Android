// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.Avatar

@Composable
internal fun AuthorPicker(author: Author, authors: List<Author>, canSwitch: Boolean, onAuthor: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val switchable = canSwitch && authors.size > 1
    val label = stringResource(R.string.composer_author, author.handle)
    Row(
        Modifier
            .then(if (switchable) Modifier.clickable(role = Role.DropdownList) { open = true } else Modifier)
            .heightIn(min = TOUCH)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        Avatar(author.avatarUrl, AVATAR)
        Text(
            author.handle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (switchable) Icon(AlohaIcons.ExpandMore, contentDescription = null)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        authors.forEach { other ->
            DropdownMenuItem(
                text = { Text(other.handle) },
                leadingIcon = { Avatar(other.avatarUrl, AVATAR) },
                trailingIcon = { if (other.id == author.id) Chosen() },
                modifier = Modifier.chosen(other.id == author.id),
                onClick = {
                    open = false
                    if (other.id != author.id) onAuthor(other.id)
                },
            )
        }
    }
}

@Composable
internal fun SpoilerField(spoiler: String, onSpoiler: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        OutlinedTextField(
            value = spoiler,
            onValueChange = onSpoiler,
            label = { Text(stringResource(R.string.composer_cw_label)) },
            placeholder = { Text(stringResource(R.string.composer_cw_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            CW_PRESETS.forEach { preset ->
                val text = stringResource(preset)
                // tapping the preset in use clears it again
                FilterChip(
                    selected = spoiler == text,
                    onClick = { onSpoiler(if (spoiler == text) "" else text) },
                    label = { Text(text) },
                )
            }
        }
    }
}

@Composable
internal fun SegmentField(
    index: Int,
    count: Int,
    value: TextFieldValue,
    state: ComposerUiState,
    actions: ComposerActions,
) {
    val posted = index < state.posted
    // a thread's posts are told apart by a label; a single post's field speaks its hint, and what is written
    val label = if (count > 1) stringResource(R.string.composer_segment_label, index + 1) else null
    Column {
        if (index == 0 && state.mentioned.isNotEmpty()) MentionChips(state.mentioned, actions::onLeaveOut)
        SegmentText(
            value,
            label,
            hint(index, state),
            state.overFrom.getOrNull(index),
            readOnly = posted || state.posting,
        ) { actions.onText(index, it) }
        if (index == 0) QuoteParts(state, actions)
        state.attachments.getOrNull(index)?.takeIf {
            it.isNotEmpty()
        }?.let { MediaStrip(it, actions, drafts = state.draftsAltText) }
        if (index == 0) OpeningExtras(state, actions)
        if (count > 1) SegmentFooter(index, posted, state, actions)
    }
}

/**
 * A post's text, coloured as it is typed, what is past the limit from [overFrom] in the error colours; [label]
 * names it within a thread, [hint] shows while it is empty.
 */
@Composable
private fun SegmentText(
    value: TextFieldValue,
    label: String?,
    hint: Int,
    overFrom: Int?,
    readOnly: Boolean,
    onText: (TextFieldValue) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highlight = remember(colors, overFrom) {
        val over = SpanStyle(color = colors.onErrorContainer, background = colors.errorContainer)
        HighlightTransformation(colors.primary, over, overFrom)
    }
    TextField(
        value = value,
        onValueChange = onText,
        readOnly = readOnly,
        label = label?.let { { Text(it) } },
        placeholder = { Text(stringResource(hint)) },
        visualTransformation = highlight,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What an empty segment says: what comes next in a thread, a quote's comment, or the opening post's prompt. */
private fun hint(index: Int, state: ComposerUiState): Int = when {
    index > 0 -> R.string.composer_placeholder_more
    state.quote != null -> R.string.composer_placeholder_quote
    else -> R.string.composer_placeholder
}

/** Whom a reply to several people is addressed to, each a chip that leaves them out. */
@Composable
private fun MentionChips(handles: List<String>, onLeaveOut: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        handles.forEach { handle ->
            val leave = stringResource(R.string.composer_mention_leave_out, handle)
            // a button, not a chip to select: a tap takes the person out
            AssistChip(
                onClick = { onLeaveOut(handle) },
                label = { Text(handle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(AlohaIcons.Close, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                },
                modifier = Modifier.semantics { contentDescription = leave },
            )
        }
    }
}

/** A post of a thread: what is left of its limit, and removing it while it is not posted yet. */
@Composable
private fun SegmentFooter(index: Int, posted: Boolean, state: ComposerUiState, actions: ComposerActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Counter(state.remaining.getOrElse(index) { 0 }, Modifier.weight(1f))
        if (index > 0 && !posted) {
            IconButton(onClick = { actions.onRemoveSegment(index) }, enabled = !state.posting) {
                Icon(AlohaIcons.Remove, stringResource(R.string.composer_remove_segment))
            }
        }
    }
}

private val CW_PRESETS = listOf(
    R.string.composer_cw_spoiler,
    R.string.composer_cw_food,
    R.string.composer_cw_politics,
    R.string.composer_cw_mental_health,
    R.string.composer_cw_eye_contact,
    R.string.composer_cw_work,
)

private val AVATAR = 32.dp

private val TOUCH = 48.dp
