// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.ConfirmDialog
import social.aloha.core.ui.readingWidth

/**
 * The composer: who it posts as, the post being answered, the content warning, the text of each post
 * in the thread, and a toolbar above the keyboard with what is left of the limit. One column centred
 * at reading width, so a tablet or a phone on its side writes at a width that reads well.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerScreen(
    state: ComposerUiState,
    segments: List<TextFieldValue>,
    spoiler: String,
    actions: ComposerActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    val title = stringResource(if (state.reply != null) R.string.composer_title_reply else R.string.composer_title)
    var previewing by rememberSaveable { mutableStateOf(false) }
    if (previewing) PreviewSheet(state, segments.map { it.text }, spoiler) { previewing = false }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = {
                    state.author?.let {
                        AuthorPicker(it, state.authors, state.posted == 0 && !state.editing, actions::onAuthor)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions::onClose) {
                        Icon(AlohaIcons.Close, stringResource(R.string.composer_close))
                    }
                },
                actions = {
                    ComposerMenu(actions) { previewing = true }
                    if (!state.postAtBottom) PostButton(state, actions)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                // capped before filling, so a wide window centres a column at reading width
                Modifier.weight(1f).readingWidth().fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = AlohaSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            ) { Writing(state, segments, spoiler, actions) }
            CompletionStrip(state.completions, state.emojis, actions, Modifier.readingWidth())
            HorizontalDivider()
            Toolbar(state, spoiler, actions, Modifier.readingWidth(), showCounter = segments.size == 1) {
                if (state.postAtBottom) PostButton(state, actions)
            }
        }
    }
}

/** The post as written: what it answers, its warning, each segment of the thread, its poll and time. */
@Composable
private fun Writing(state: ComposerUiState, segments: List<TextFieldValue>, spoiler: String, actions: ComposerActions) {
    state.resume?.let { ResumeCard(it, segments, spoiler, actions::onResume) }
    state.reply?.let { ReplyLine(it) }
    if (state.editing) {
        Text(
            stringResource(R.string.composer_edit_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.spoilerShown) SpoilerField(spoiler) { actions.onSpoiler(true, it) }
    segments.forEachIndexed { index, value ->
        SegmentField(index, segments.size, value, state, actions)
        if (index == 0) state.poll?.let { PollEditor(state, it, actions::onPoll) }
    }
    GamesHint(state.games)
    state.scheduledAt?.let { ScheduleLine(it, actions::onPickSchedule) { actions.onSchedule(null) } }
    TextButton(onClick = actions::onAddSegment, enabled = state.threadable) {
        Icon(AlohaIcons.AddToThread, contentDescription = null)
        Text(stringResource(R.string.composer_add_segment), Modifier.padding(start = AlohaSpacing.xs))
    }
}

/** How the post will look, and what is kept apart from it: the posts waiting for their time, and the drafts. */
@Composable
private fun ComposerMenu(actions: ComposerActions, onPreview: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.composer_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_preview)) },
                onClick = {
                    open = false
                    onPreview()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drafts_title)) },
                onClick = {
                    open = false
                    actions.onDrafts()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_scheduled_posts)) },
                onClick = {
                    open = false
                    actions.onScheduledPosts()
                },
            )
        }
    }
}

@Composable
private fun PostButton(state: ComposerUiState, actions: ComposerActions) {
    val posting = stringResource(R.string.composer_posting)
    val label = when {
        state.editing -> R.string.composer_save
        state.posted > 0 -> R.string.composer_post_again
        state.scheduledAt != null -> R.string.composer_post_schedule
        !state.uploaded && state.canWait -> R.string.composer_post_later
        state.reply != null -> R.string.composer_post_reply
        else -> R.string.composer_post
    }
    // asked first where the writer chose so; an edit saves as it always did
    var asking by rememberSaveable { mutableStateOf(false) }
    val confirm = state.confirmBeforePosting && !state.editing
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.composer_confirm_title),
            body = stringResource(R.string.composer_confirm_body),
            action = stringResource(label),
            onDismiss = { asking = false },
            onConfirm = actions::onPost,
        )
    }
    Button(
        onClick = { if (confirm) asking = true else actions.onPost() },
        enabled = state.canPost,
        modifier = Modifier.padding(end = AlohaSpacing.s),
    ) {
        if (state.posting) {
            CircularProgressIndicator(
                Modifier.size(PROGRESS).semantics {
                    contentDescription = posting
                    liveRegion = LiveRegionMode.Polite
                },
                strokeWidth = 2.dp,
            )
        } else {
            Text(stringResource(label))
        }
    }
}

/** The draft put aside last, to go back to instead of starting anew, while nothing is written yet. */
@Composable
private fun ResumeCard(resume: ResumeUi, segments: List<TextFieldValue>, spoiler: String, onResume: (String) -> Unit) {
    if (segments.any { it.text.isNotBlank() } || spoiler.isNotBlank()) return
    OutlinedCard(
        onClick = { onResume(resume.draftId) },
        modifier = Modifier.fillMaxWidth().padding(top = AlohaSpacing.s),
    ) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            Text(stringResource(R.string.composer_resume), style = MaterialTheme.typography.titleSmall)
            if (resume.excerpt.isNotBlank()) {
                Text(
                    resume.excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReplyLine(reply: ReplyContext) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = AlohaSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.composer_replying_to, reply.author),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { open = !open }) {
                Text(stringResource(if (open) R.string.composer_reply_hide else R.string.composer_reply_show))
            }
        }
        if (open) {
            Text(
                reply.excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GamesHint(games: List<ComposerGames.Kind>) {
    games.forEach { kind ->
        val text = when (kind) {
            ComposerGames.Kind.Dice -> R.string.composer_game_dice
            ComposerGames.Kind.Flip -> R.string.composer_game_flip
            ComposerGames.Kind.Pick -> R.string.composer_game_pick
        }
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val PROGRESS = 20.dp
