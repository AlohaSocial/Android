// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.LogArea
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.ComposerKey
import timber.log.Timber

/** Which of the composer's dialogs is open; saved, so turning the phone keeps it open. */
private class Dialogs(
    val editing: MutableState<String?>,
    val undescribed: MutableState<Boolean>,
    val discarding: MutableState<Boolean>,
    /** A short just recorded, waiting for the writer's answer about `#shorts`. */
    val short: MutableState<String?>,
    /** The picker open over the composer, if any. */
    val picker: MutableState<Picker?>,
)

/** The pickers that open over the composer, one at a time. */
private enum class Picker { Gifs, NextcloudFile, Schedule }

/**
 * The composer for [key]; [onDone] leaves it, once posted, discarded or kept as a draft;
 * [onScheduledPosts] and [onDrafts] open those lists, [onSearch] search for who a mention could not find.
 */
@Composable
public fun ComposerRoute(
    key: ComposerKey,
    onDone: () -> Unit,
    onScheduledPosts: () -> Unit,
    onDrafts: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
) {
    val viewModel = hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val done by rememberUpdatedState(onDone)
    val dialogs = Dialogs(
        rememberSaveable { mutableStateOf(null) },
        rememberSaveable { mutableStateOf(false) },
        rememberSaveable { mutableStateOf(false) },
        rememberSaveable { mutableStateOf(null) },
        rememberSaveable { mutableStateOf(null) },
    )
    val scheduled by rememberUpdatedState(onScheduledPosts)
    val drafts by rememberUpdatedState(onDrafts)
    val search by rememberUpdatedState(onSearch)
    val elsewhere = Elsewhere({ scheduled() }, { drafts() }) { search(it) }
    val actions = rememberActions(viewModel, state, dialogs, elsewhere) { done() }
    val hue = MaterialTheme.colorScheme.primary.toArgb()
    LaunchedEffect(hue) { viewModel.cards.onHue(hue) }

    BackHandler(onBack = actions::onClose)
    QueuedEffect(state.queued)
    LaunchedEffect(state.done) { if (state.done) done() }
    FailureSnackbar(state, snackbars, viewModel::onFailureShown)
    val drop = rememberMediaDrop(viewModel::onPicked)
    ComposerScreen(
        state,
        viewModel.segments,
        viewModel.spoiler,
        actions,
        modifier.dragAndDropTarget({ carriesMedia(it.toAndroidDragEvent().clipDescription) }, drop),
        snackbars,
    )
    ComposerDialogs(state, viewModel, dialogs) { done() }
}

/** The lists the composer opens, and search. */
private class Elsewhere(val scheduledPosts: () -> Unit, val drafts: () -> Unit, val search: (String) -> Unit)

/**
 * The composer's actions, made once; what they decide on (the state, what the pickers accept) is
 * read as it is when they run, never as it was when they were made.
 */
@Composable
private fun rememberActions(
    viewModel: ComposerViewModel,
    state: ComposerUiState,
    dialogs: Dialogs,
    elsewhere: Elsewhere,
    done: () -> Unit,
): ComposerActions {
    val current by rememberUpdatedState(state)
    val types by rememberUpdatedState(viewModel.acceptedTypes)
    val pickMedia = rememberLauncherForActivityResult(
        // the picker takes at least two, and the ViewModel keeps to the room there is
        ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, state.maxAttachments)),
    ) { viewModel.onPicked(it) }
    val pickFiles =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.onPicked(it) }
    val camera = rememberCamera(viewModel, state, dialogs)
    val context = LocalContext.current
    return remember(viewModel, dialogs) {
        object : ComposerActions, QuoteActions by viewModel.quoting {
            override fun onClose() {
                if (viewModel.hasWriting && current.posted == 0) dialogs.discarding.value = true else done()
            }

            override fun onPost() {
                if (current.warnMissingDescription && current.undescribed) {
                    dialogs.undescribed.value = true
                } else {
                    viewModel.onPost()
                }
            }

            override fun onText(index: Int, value: TextFieldValue) = viewModel.onText(index, value)

            override fun onSpoiler(shown: Boolean, text: String) = viewModel.onSpoiler(shown, text)

            override fun onVisibility(visibility: Visibility) = viewModel.onVisibility(visibility)

            override fun onLanguage(language: String?) = viewModel.onLanguage(language)

            override fun onQuotePolicy(policy: QuotePolicy) = viewModel.onQuotePolicy(policy)

            override fun onSuggestion(suggestion: Suggestion) = viewModel.onSuggestion(suggestion)

            override fun onFindPeople(query: String) = elsewhere.search(query)

            override fun onEmoji(emoji: CustomEmoji) = viewModel.onEmoji(emoji)

            override fun onAddSegment() = viewModel.onSegments()

            override fun onRemoveSegment(index: Int) = viewModel.onSegments(index)

            override fun onAuthor(id: String) = viewModel.onAuthor(id)

            override fun onLeaveOut(handle: String) =
                viewModel.onText(0, ComposerText.without(viewModel.segments.first(), handle))

            override fun onPickSchedule() {
                dialogs.picker.value = Picker.Schedule
            }

            override fun onSchedule(at: Instant?) {
                viewModel.scheduledAt.value = at
            }

            override fun onScheduledPosts() = elsewhere.scheduledPosts()

            override fun onDrafts() = elsewhere.drafts()

            override fun onPoll(poll: PollUi?) {
                viewModel.poll.value = poll
            }

            override fun onPickMedia() =
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

            override fun onPickFiles() = pickFiles.launch(types)

            override fun onCapture(capture: Capture) = camera(capture)

            override fun onGifs() {
                dialogs.picker.value = Picker.Gifs
            }

            override fun onNextcloudFile() {
                dialogs.picker.value = Picker.NextcloudFile
            }

            override fun onPaste() {
                val pasted = clipboardMedia(context)
                if (pasted.isEmpty()) {
                    viewModel.attachments.failure.value = AttachFailure.NothingToPaste
                } else {
                    viewModel.onPicked(pasted)
                }
            }

            override fun onEditMedia(id: String) {
                dialogs.editing.value = id
            }

            override fun onRemoveMedia(id: String) = viewModel.attachments.remove(id)

            override fun onOrderMedia(ids: List<String>) = viewModel.attachments.order(ids)

            override fun onRetryMedia(id: String) = viewModel.attachments.retry(id)

            override fun onSensitive(sensitive: Boolean) {
                viewModel.attachments.sensitive.value = sensitive
            }

            override fun onStory(on: Boolean) = viewModel.story.onStory(on)

            override fun onStorySeconds(seconds: Int) = viewModel.story.onSeconds(seconds)

            override fun onCard(on: Boolean) {
                viewModel.cards.onCard(on)
                if (on) viewModel.cards.onText(viewModel.segments.first().text)
            }

            override fun onCardBackground(index: Int) {
                viewModel.cards.onBackground(index)
                viewModel.cards.onText(viewModel.segments.first().text)
            }
        }
    }
}

/**
 * The system camera, for a photo, a video or a short. A short is tagged `#shorts` only as the writer
 * chose; the first time, they are asked.
 */
@Composable
private fun rememberCamera(viewModel: ComposerViewModel, state: ComposerUiState, dialogs: Dialogs): (Capture) -> Unit {
    val context = LocalContext.current
    val tag by rememberUpdatedState(state.tagShorts)
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var short by rememberSaveable { mutableStateOf(false) }
    val onCaptured = { taken: Boolean ->
        val uri = target?.toUri()
        if (!taken && uri != null) CaptureFiles.discard(context, uri)
        if (taken && uri != null) {
            when {
                !short -> viewModel.onPicked(listOf(uri))
                tag == null -> dialogs.short.value = uri.toString()
                else -> viewModel.onPicked(listOf(uri), tagShort = tag == true)
            }
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onCaptured)
    val contract = remember { CaptureVideo() }
    val video = rememberLauncherForActivityResult(contract, onCaptured)
    return { capture ->
        val isPhoto = capture == Capture.Photo
        val uri = CaptureFiles.target(context, if (isPhoto) ".jpg" else ".mp4")
        target = uri.toString()
        short = capture is Capture.Short
        contract.seconds = (capture as? Capture.Short)?.seconds
        runCatching { if (isPhoto) photo.launch(uri) else video.launch(uri) }.onFailure {
            Timber.tag(LogArea.Compose.name).w("Camera not opened: %s", it.javaClass.simpleName)
        }
    }
}

@Composable
private fun ComposerDialogs(state: ComposerUiState, viewModel: ComposerViewModel, dialogs: Dialogs, done: () -> Unit) {
    var editing by dialogs.editing
    var undescribed by dialogs.undescribed
    var discarding by dialogs.discarding
    editing?.let { id ->
        // an attachment removed while its sheet was open closes the sheet
        val attachment = state.attachments.flatten().firstOrNull { it.id == id }
        if (attachment == null) {
            editing = null
        } else {
            val info by produceState<VideoInfo?>(null, id) { value = viewModel.edits.info(id) }
            MediaEditor(attachment, info) { description, focus, change ->
                viewModel.attachments.describe(id, description, focus)
                when (change) {
                    is MediaChange.Filter -> viewModel.edits.applyFilter(id, change.filter)
                    is MediaChange.Video -> viewModel.edits.editVideo(id, change.edit)
                    null -> Unit
                }
                editing = null
            }
        }
    }
    if (undescribed) {
        UndescribedDialog(
            onDescribe = {
                undescribed = false
                editing = state.attachments.flatten().firstOrNull { it.description.isBlank() }?.id
            },
            onPostAnyway = {
                undescribed = false
                viewModel.onPost()
            },
        )
    }
    dialogs.short.value?.let { recorded ->
        ShortsDialog { tag ->
            dialogs.short.value = null
            viewModel.onPicked(listOf(recorded.toUri()), tagShort = tag, remember = true)
        }
    }
    LibraryDialogs(viewModel, dialogs)
    if (state.confirmQuote) QuoteConfirmDialog(viewModel.quoting::onConfirmQuote, viewModel.quoting::onCancelQuote)
    if (dialogs.picker.value == Picker.Schedule) {
        ScheduleDialog(
            state.scheduledAt,
            now = Instant::now,
            onPick = {
                dialogs.picker.value = null
                viewModel.scheduledAt.value = it
            },
            onDismiss = { dialogs.picker.value = null },
        )
    }
    if (discarding) {
        LeaveDialog(
            state.editing,
            onSave = {
                discarding = false
                done()
            },
            onDiscard = {
                discarding = false
                viewModel.drafts.discard()
                done()
            },
            onKeep = { discarding = false },
        )
    }
}

/**
 * Says a queued post goes out later, and asks, once, to be allowed to say so again when it cannot go
 * out: the notification is the only word the writer gets once the composer has gone.
 */
@Composable
private fun QueuedEffect(queued: Boolean) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(queued) {
        if (!queued) return@LaunchedEffect
        Toast.makeText(context, R.string.composer_queued, Toast.LENGTH_LONG).show()
        val asked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (asked) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun LibraryDialogs(viewModel: ComposerViewModel, dialogs: Dialogs) {
    var picker by dialogs.picker
    if (picker == Picker.Gifs) {
        val library by viewModel.library.gifs.collectAsStateWithLifecycle()
        GifSheet(
            library,
            onQuery = viewModel.library::onQuery,
            onMore = viewModel.library::onMore,
            onPick = { gif ->
                picker = null
                viewModel.library.onGif(gif)
            },
            onDismiss = { picker = null },
        )
    }
    if (picker == Picker.NextcloudFile) {
        val recent by produceState(emptyList<String>()) { value = viewModel.library.recentPaths() }
        NextcloudFileDialog(
            recent,
            onAttach = { path ->
                picker = null
                viewModel.library.onNextcloudFile(path)
            },
            onDismiss = { picker = null },
        )
    }
}

/** Asked once, after the first short: whether shorts get `#shorts`, which then stays the choice. */
@Composable
private fun ShortsDialog(onChoice: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice(false) },
        title = { Text(stringResource(R.string.composer_shorts_title)) },
        text = { Text(stringResource(R.string.composer_shorts_body)) },
        confirmButton = {
            TextButton(onClick = { onChoice(true) }) { Text(stringResource(R.string.composer_shorts_yes)) }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(false) }) { Text(stringResource(R.string.composer_shorts_no)) }
        },
    )
}

@Composable
private fun LeaveDialog(editing: Boolean, onSave: () -> Unit, onDiscard: () -> Unit, onKeep: () -> Unit) {
    // an edit is never kept as a draft: the post stays as it was, or changes
    AlertDialog(
        onDismissRequest = onKeep,
        title = {
            Text(stringResource(if (editing) R.string.composer_leave_edit_title else R.string.composer_leave_title))
        },
        text = if (editing) null else ({ Text(stringResource(R.string.composer_leave_body)) }),
        confirmButton = {
            if (!editing) TextButton(onClick = onSave) { Text(stringResource(R.string.composer_save_draft)) }
        },
        dismissButton = {
            // the dialog lays its buttons out in a row that wraps, so three fit a narrow phone
            Row {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.composer_discard)) }
                TextButton(onClick = onKeep) { Text(stringResource(R.string.composer_keep_editing)) }
            }
        },
    )
}

/** A warning, never a block: posting without descriptions stays one tap away. */
@Composable
private fun UndescribedDialog(onDescribe: () -> Unit, onPostAnyway: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDescribe,
        title = { Text(stringResource(R.string.composer_undescribed_title)) },
        text = { Text(stringResource(R.string.composer_undescribed_body)) },
        confirmButton = {
            TextButton(onClick = onDescribe) { Text(stringResource(R.string.composer_undescribed_add)) }
        },
        dismissButton = {
            TextButton(onClick = onPostAnyway) { Text(stringResource(R.string.composer_undescribed_post)) }
        },
    )
}

@Composable
private fun FailureSnackbar(state: ComposerUiState, snackbars: SnackbarHostState, onShown: () -> Unit) {
    val message = attachMessage(state.attachFailure) ?: editMessage(state.editFailure) ?: postMessage(state.failure)
    LaunchedEffect(state.failure, state.attachFailure, state.editFailure) {
        if (message != null) {
            onShown()
            snackbars.showSnackbar(message)
        }
    }
}

@Composable
private fun attachMessage(failure: AttachFailure?): String? = when (failure) {
    is AttachFailure.Unsupported -> stringResource(R.string.composer_attach_unsupported)

    is AttachFailure.TooLarge ->
        stringResource(
            R.string.composer_attach_too_large,
            stringResource(R.string.composer_size_mb, Attachments.megabytes(failure.limitBytes)),
        )

    AttachFailure.Unreadable -> stringResource(R.string.composer_attach_unreadable)

    AttachFailure.NotFound -> stringResource(R.string.composer_attach_not_found)

    AttachFailure.NothingToPaste -> stringResource(R.string.composer_attach_nothing_to_paste)

    null -> null
}

@Composable
private fun editMessage(failure: EditFailure?): String? = when (failure) {
    is EditFailure.StillTooLarge ->
        stringResource(
            R.string.composer_edit_still_too_large,
            stringResource(R.string.composer_size_mb, Attachments.megabytes(failure.limitBytes)),
        )

    EditFailure.Failed -> stringResource(R.string.composer_edit_failed)

    null -> null
}

@Composable
private fun postMessage(failure: PostFailure?): String? = when (failure) {
    is PostFailure.Refused -> failure.message?.let { stringResource(R.string.composer_refused, it) }
        ?: stringResource(R.string.composer_refused_unknown)

    is PostFailure.Unreached -> stringResource(R.string.composer_unreached)

    PostFailure.ReplyNotFound -> stringResource(R.string.composer_reply_not_found)

    PostFailure.CardFailed -> stringResource(R.string.composer_card_failed)

    null -> null
}
