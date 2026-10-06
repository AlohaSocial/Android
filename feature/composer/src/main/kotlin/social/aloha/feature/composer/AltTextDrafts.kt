// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.datastore.IntelligenceChoices
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.intelligence.Drafted
import social.aloha.core.intelligence.Intelligence

/** How far drafting every undescribed picture has come: [done] of [of]. */
internal data class DraftProgress(val done: Int, val of: Int)

/** Whether alt text can be drafted, and how far a batch has come. */
internal data class AltTextState(val on: Boolean = false, val progress: DraftProgress? = null)

/**
 * Alt text drafted on the device: for one picture from its editor, where the writer sees it at once, or
 * for every picture still without a description, in turn. A description carrying the [mark] that the
 * writer has not looked at in its editor since the composer opened waits to be checked, and posting
 * says so first: a draft kept and opened again, or one the app was closed over, is checked again too.
 */
internal class AltTextDrafts(
    private val intelligence: Intelligence,
    choices: Flow<IntelligenceChoices>,
    private val attachments: Attachments,
    private val scope: CoroutineScope,
) {
    private val own = MutableStateFlow(AltTextState())
    private var batch: Job? = null
    private val seen = HashSet<String>()

    val mark: String get() = intelligence.altTextMark

    val state: StateFlow<AltTextState> = combine(choices, own) { chosen, state ->
        state.copy(on = chosen.altText && intelligence.describesPictures)
    }.stateIn(scope, SharingStarted.Eagerly, AltTextState())

    fun offers(attachment: Attachment, on: Boolean = state.value.on): Boolean =
        on && attachment.isPicture && attachment.source != null

    suspend fun draft(attachment: Attachment): Drafted =
        attachment.source?.let { intelligence.describe(it) } ?: Drafted.Failed

    /** Drafts every picture still without a description, or [only] that one. */
    fun draftAll(only: String? = null) {
        val waiting = present().filter { (only == null || it.id == only) && it.description.isBlank() && offers(it) }
        if (waiting.isEmpty()) return
        batch = scope.launch {
            try {
                waiting.forEachIndexed { index, picture ->
                    own.update { it.copy(progress = DraftProgress(index, waiting.size)) }
                    val drafted = draft(picture) as? Drafted.Text ?: return@forEachIndexed
                    // a description typed meanwhile wins over the draft
                    val now = present().firstOrNull { it.id == picture.id }?.takeIf { it.description.isBlank() }
                    if (now != null) attachments.describe(now.id, drafted.text, now.focus)
                }
            } finally {
                own.update { it.copy(progress = null) }
            }
        }
    }

    fun cancel() {
        batch?.cancel()
    }

    fun checked(id: String) {
        seen += id
    }

    /** The drafted descriptions nobody looked at, of the attachments still there. */
    fun unchecked(): List<String> = present().filter { it.id !in seen && it.description.endsWith(mark) }.map { it.id }

    private fun present() = attachments.byPost.value.flatten()
}

/**
 * A description as its editor holds it: the text typed, and for one drafted on the device and left as
 * drafted, the [mark] after it, with [checking] over it. The writer's first edit drops both: the words are
 * theirs then, and so is the editorial responsibility (the AI Act asks for no label on reviewed text).
 */
@Stable
internal class Described(description: String, private val generatedMark: String, unchecked: Boolean) {
    private val marked = generatedMark.isNotEmpty() && description.endsWith(generatedMark)
    var text by mutableStateOf(if (marked) description.removeSuffix(generatedMark) else description)
        private set
    private var generated by mutableStateOf(marked)
    var checking by mutableStateOf(marked && unchecked)
        private set

    /** How many drafts arrived, so the field takes the focus back after each. */
    var drafts by mutableIntStateOf(0)
        private set

    val mark: String? get() = generatedMark.takeIf { generated }

    val result: String get() = if (generated && text.isNotBlank()) text + generatedMark else text

    fun onDrafted(drafted: String) {
        text = drafted.removeSuffix(generatedMark)
        generated = true
        checking = true
        drafts++
    }

    fun onTyped(typed: String) {
        text = typed
        checking = false
        generated = false
    }
}

/** The editor's way to a draft: a button while there is nothing written, a spinner while it is drafted. */
@Composable
internal fun DraftButton(busy: Boolean, failed: Boolean, onDraft: () -> Unit) {
    val drafting = stringResource(R.string.composer_alt_drafting_one)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            TextButton(
                onClick = onDraft,
                enabled = !busy,
                modifier = Modifier.semantics { if (busy) stateDescription = drafting },
            ) {
                Icon(AlohaIcons.Intelligence, contentDescription = null, Modifier.size(AlohaSpacing.m))
                Text(stringResource(R.string.composer_alt_draft), Modifier.padding(start = AlohaSpacing.s))
            }
            if (busy) CircularProgressIndicator(Modifier.size(AlohaSpacing.m))
        }
        if (failed) {
            Text(
                stringResource(R.string.composer_alt_draft_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Over a description drafted on the device until the writer changes it. */
@Composable
internal fun GeneratedNote() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        Icon(AlohaIcons.Intelligence, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.composer_alt_generated),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Drafting every undescribed picture, one after the other, with a way to stop. */
@Composable
internal fun DraftingDialog(progress: DraftProgress, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.composer_alt_drafting)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                Text(
                    stringResource(R.string.composer_alt_drafting_count, progress.done + 1, progress.of),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                LinearProgressIndicator(
                    progress = { (progress.done + 1f) / progress.of },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.composer_alt_drafting_stop)) }
        },
    )
}

/** Before posting drafts nobody has looked at: a warning, and checking them is one tap away. */
@Composable
internal fun UncheckedDialog(count: Int, onCheck: () -> Unit, onPostAnyway: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.composer_alt_unchecked_title, count, count)) },
        text = { Text(stringResource(R.string.composer_alt_unchecked_body)) },
        confirmButton = { TextButton(onClick = onCheck) { Text(stringResource(R.string.composer_alt_check)) } },
        dismissButton = {
            TextButton(onClick = onPostAnyway) { Text(stringResource(R.string.composer_undescribed_post)) }
        },
    )
}
