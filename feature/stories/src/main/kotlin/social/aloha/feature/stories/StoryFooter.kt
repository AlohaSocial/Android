// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Story

/** The emoji a story takes with one tap; the server allows any. */
internal val QUICK_REACTIONS = listOf("❤️", "🔥", "😂", "😮", "😢", "👏")

/** What a reaction or reply came to, said once it is answered. */
private enum class Sent { Yes, No }

/**
 * Under the story: its caption, and for the reader's own how many watched and a way to end it, for
 * anybody else's an emoji or a reply. [onBusy] pauses the story while the reader is busy with any of it.
 */
@Composable
internal fun BoxScope.Footer(
    reel: StoryReel,
    story: Story,
    actions: StoryActions,
    onBusy: (Boolean) -> Unit,
    onGone: () -> Unit,
) {
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM))))
            .navigationBarsPadding().imePadding().padding(AlohaSpacing.s),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        story.caption?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color.White, maxLines = CAPTION_LINES, overflow = TextOverflow.Ellipsis)
        }
        if (reel.own) Own(story, actions, onBusy, onGone) else Respond(reel, story, actions, onBusy)
    }
}

@Composable
private fun Own(story: Story, actions: StoryActions, onBusy: (Boolean) -> Unit, onGone: () -> Unit) {
    var audience by rememberSaveable(story.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(story.id) { mutableStateOf(false) }
    var failed by rememberSaveable(story.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(audience, deleting) { onBusy(audience || deleting) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { audience = true }) {
            val views = story.viewCount ?: 0
            Text(pluralStringResource(R.plurals.stories_views, views, views), color = Color.White)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { deleting = true }) {
            Icon(AlohaIcons.Delete, stringResource(R.string.stories_delete), tint = Color.White)
        }
    }
    if (failed) Notice(stringResource(R.string.stories_delete_failed))
    if (audience) AudienceSheet(story.id, actions) { audience = false }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.stories_delete_title)) },
            text = { Text(stringResource(R.string.stories_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch { if (actions.delete(story.id)) onGone() else failed = true }
                }) { Text(stringResource(R.string.stories_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** An emoji with one tap, or a reply typed out, both to the poster alone. */
@Composable
private fun Respond(reel: StoryReel, story: Story, actions: StoryActions, onBusy: (Boolean) -> Unit) {
    var text by rememberSaveable(story.id) { mutableStateOf("") }
    var sent by rememberSaveable(story.id) { mutableStateOf<Sent?>(null) }
    val scope = rememberCoroutineScope()
    val send = { reply: suspend () -> Boolean ->
        scope.launch { sent = if (reply()) Sent.Yes else Sent.No }
        Unit
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs)) {
        QUICK_REACTIONS.forEach { emoji ->
            TextButton(onClick = { send { actions.react(story.id, emoji) } }) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    val name = reel.account.bestDisplayName
    val reply = {
        val words = text.trim()
        if (words.isNotEmpty()) {
            text = ""
            send { actions.reply(story.id, words) }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.stories_reply, name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { reply() }),
        trailingIcon = {
            IconButton(onClick = reply, enabled = text.isNotBlank()) {
                Icon(AlohaIcons.Send, stringResource(R.string.stories_send))
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = BORDER),
            focusedPlaceholderColor = Color.White.copy(alpha = BORDER),
            unfocusedPlaceholderColor = Color.White.copy(alpha = BORDER),
            focusedTrailingIconColor = Color.White,
            unfocusedTrailingIconColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().onFocusChanged { onBusy(it.isFocused) },
    )
    sent?.let {
        Notice(stringResource(if (it == Sent.Yes) R.string.stories_sent else R.string.stories_send_failed, name))
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Who watched one of the reader's own stories, and what they sent back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudienceSheet(id: String, actions: StoryActions, onDismiss: () -> Unit) {
    var audience by remember(id) { mutableStateOf<Audience?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        audience = actions.audience(id)
        loaded = true
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val shown = audience
        when {
            !loaded -> Unit

            shown == null -> Text(stringResource(R.string.stories_audience_failed), Modifier.padding(AlohaSpacing.m))

            shown.viewers.isEmpty() && shown.reactions.isEmpty() ->
                Text(stringResource(R.string.stories_audience_none), Modifier.padding(AlohaSpacing.m))

            else -> LazyColumn {
                items(shown.reactions, key = { "r" + it.id }) { reaction ->
                    ListItem(
                        headlineContent = { Text(reaction.account.bestDisplayName) },
                        supportingContent = {
                            Text(listOfNotNull(reaction.reaction, reaction.comment).joinToString(" "))
                        },
                    )
                }
                items(shown.viewers, key = { "v" + it.id }) { viewer ->
                    ListItem(headlineContent = { Text(viewer.bestDisplayName) })
                }
            }
        }
    }
}

private const val SCRIM = 0.6f
private const val BORDER = 0.7f
private const val CAPTION_LINES = 3
