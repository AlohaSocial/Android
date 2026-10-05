// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Visibility

@Composable
internal fun Toolbar(
    state: ComposerUiState,
    spoiler: String,
    actions: ComposerActions,
    modifier: Modifier = Modifier,
    showCounter: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    var emojis by remember { mutableStateOf(false) }
    var languages by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        // more controls than a narrow phone is wide: they scroll, and the count stays in view
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaButtons(state, actions)
            OutlinedIconToggleButton(
                checked = state.spoilerShown,
                onCheckedChange = { actions.onSpoiler(it, spoiler) },
            ) {
                Icon(AlohaIcons.ContentWarning, stringResource(R.string.composer_cw))
            }
            VisibilityMenu(state, actions::onVisibility)
            ScheduleButton(state, actions)
            IconButton(onClick = { languages = true }) {
                val name = languageName(state.language) ?: stringResource(R.string.composer_language_none)
                Icon(AlohaIcons.Language, stringResource(R.string.composer_language, name))
            }
            if (state.emojis.isNotEmpty()) {
                IconButton(onClick = {
                    emojis = true
                }) { Icon(AlohaIcons.Emoji, stringResource(R.string.composer_emoji)) }
            }
            if (state.quotePolicies.isNotEmpty()) QuoteMenu(state, actions::onQuotePolicy)
        }
        // in a thread each post shows its own count instead
        if (showCounter) {
            Counter(
                state.remaining.firstOrNull() ?: 0,
                Modifier.padding(horizontal = AlohaSpacing.s),
                alignEnd = true,
            )
        }
        trailing()
    }
    if (emojis) EmojiSheet(state.emojis, onPick = actions::onEmoji, onDismiss = { emojis = false })
    if (languages) {
        LanguageDialog(
            state.language,
            state.detected,
            onPick = actions::onLanguage,
            onDismiss = { languages = false },
        )
    }
}

/**
 * Adding pictures, videos or files, while the post has room for them, and marking them sensitive; or
 * a poll instead, on an opening post without media.
 */
@Composable
private fun MediaButtons(state: ComposerUiState, actions: ComposerActions) {
    val attached = state.attachments.maxOfOrNull { it.size } ?: 0
    val room = state.poll == null && state.attachments.any { it.size < state.maxAttachments }
    IconButton(onClick = actions::onPickMedia, enabled = room && !state.posting) {
        Icon(AlohaIcons.AddMedia, stringResource(R.string.composer_add_media))
    }
    IconButton(onClick = actions::onPickFiles, enabled = room && !state.posting) {
        Icon(AlohaIcons.AttachFile, stringResource(R.string.composer_add_file))
    }
    CameraMenu(enabled = room && !state.posting, onCapture = actions::onCapture)
    MoreSourcesMenu(state, enabled = room && !state.posting, actions)
    ShareAs(state, actions)
    PollToggle(state, actions)
    if (attached > 0) {
        OutlinedIconToggleButton(checked = state.mediaSensitive, onCheckedChange = actions::onSensitive) {
            Icon(AlohaIcons.Sensitive, stringResource(R.string.composer_media_sensitive))
        }
    }
}

/** What else attaches: the clipboard, and what the server offers of its own, GIFs and Nextcloud files. */
@Composable
private fun MoreSourcesMenu(state: ComposerUiState, enabled: Boolean, actions: MediaActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(AlohaIcons.AttachMore, stringResource(R.string.composer_more_sources))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val choices = listOfNotNull(
                stringResource(R.string.composer_paste) to actions::onPaste,
                (stringResource(R.string.composer_gifs) to actions::onGifs).takeIf { state.gifLibrary },
                (stringResource(R.string.composer_nextcloud_file) to actions::onNextcloudFile)
                    .takeIf { state.nextcloudFiles },
            )
            choices.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

/** The camera: a photo, a video, or a short of one of the set lengths. */
@Composable
private fun CameraMenu(enabled: Boolean, onCapture: (Capture) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(AlohaIcons.Camera, stringResource(R.string.composer_camera))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val choices = listOf(
                Capture.Photo to stringResource(R.string.composer_camera_photo),
                Capture.Video to stringResource(R.string.composer_camera_video),
            ) + Capture.SHORT_SECONDS.map {
                Capture.Short(it) to pluralStringResource(R.plurals.composer_camera_short, it, it)
            }
            choices.forEach { (capture, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onCapture(capture)
                    },
                )
            }
        }
    }
}

@Composable
private fun VisibilityMenu(state: ComposerUiState, onVisibility: (Visibility) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = stringResource(state.visibility.label)
    // who a post reaches is settled once it is out; an edit cannot change it
    Box {
        IconButton(onClick = { open = true }, enabled = !state.editing) {
            Icon(state.visibility.icon, stringResource(R.string.composer_visibility, current))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.visibilities.forEach { visibility ->
                DropdownMenuItem(
                    text = { Text(stringResource(visibility.label)) },
                    leadingIcon = { Icon(visibility.icon, contentDescription = null) },
                    trailingIcon = { if (visibility == state.visibility) Chosen() },
                    modifier = Modifier.chosen(visibility == state.visibility),
                    onClick = {
                        open = false
                        onVisibility(visibility)
                    },
                )
            }
            if (state.visibilityClamped) {
                Text(
                    stringResource(R.string.composer_visibility_clamped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun QuoteMenu(state: ComposerUiState, onQuotePolicy: (QuotePolicy) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(AlohaIcons.Quote, stringResource(R.string.composer_quote, stringResource(state.quotePolicy.label)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.quotePolicies.forEach { policy ->
                DropdownMenuItem(
                    text = { Text(stringResource(policy.label)) },
                    trailingIcon = { if (policy == state.quotePolicy) Chosen() },
                    modifier = Modifier.chosen(policy == state.quotePolicy),
                    onClick = {
                        open = false
                        onQuotePolicy(policy)
                    },
                )
            }
        }
    }
}

/** The mark on the chosen item of a menu; [chosen] says so to a screen reader. */
@Composable
internal fun Chosen() = Icon(AlohaIcons.Check, contentDescription = null)

/** What is left of the limit; turns to the error colour, and says so aloud, once the post is too long. */
@Composable
internal fun Counter(remaining: Int, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val over = remaining < 0
    val spoken = if (over) {
        pluralStringResource(R.plurals.composer_over, -remaining, -remaining)
    } else {
        pluralStringResource(R.plurals.composer_remaining, remaining, remaining)
    }
    Text(
        remaining.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (alignEnd) TextAlign.End else null,
        modifier = modifier.semantics {
            contentDescription = spoken
            if (over) liveRegion = LiveRegionMode.Polite
        },
    )
}

private val Visibility.label: Int
    get() = when (this) {
        Visibility.Public -> R.string.composer_visibility_public
        Visibility.Unlisted -> R.string.composer_visibility_unlisted
        Visibility.Private -> R.string.composer_visibility_private
        Visibility.Direct, Visibility.Unknown -> R.string.composer_visibility_direct
    }

private val Visibility.icon
    get() = when (this) {
        Visibility.Public -> AlohaIcons.VisibilityPublic
        Visibility.Unlisted -> AlohaIcons.VisibilityUnlisted
        Visibility.Private -> AlohaIcons.VisibilityPrivate
        Visibility.Direct, Visibility.Unknown -> AlohaIcons.VisibilityDirect
    }

private val QuotePolicy.label: Int
    get() = when (this) {
        QuotePolicy.Anyone -> R.string.composer_quote_anyone
        QuotePolicy.Followers -> R.string.composer_quote_followers
        QuotePolicy.Nobody -> R.string.composer_quote_nobody
    }
