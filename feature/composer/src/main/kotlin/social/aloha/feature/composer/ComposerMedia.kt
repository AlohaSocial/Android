// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.sync.UploadState

/** The attachments of one post, each a tile that opens its description and focal point. */
@Composable
internal fun MediaStrip(attachments: List<Attachment>, actions: ComposerActions) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        items(attachments, key = { it.id }) { MediaTile(it, actions) }
    }
}

@Composable
private fun MediaTile(attachment: Attachment, actions: ComposerActions) {
    val status = uploadLabel(attachment.upload)
    val edit = stringResource(R.string.composer_media_edit)
    val described = attachment.description.isNotBlank()
    val summary = listOfNotNull(
        attachment.fileName,
        status,
        stringResource(if (described) R.string.composer_media_described else R.string.composer_media_undescribed),
    ).joinToString(", ")
    Box(Modifier.size(TILE)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClickLabel = edit, role = Role.Button) { actions.onEditMedia(attachment.id) }
                .semantics(mergeDescendants = true) { contentDescription = summary },
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.isPicture) {
                AsyncImage(
                    attachment.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    attachment.fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(AlohaSpacing.xs),
                )
            }
            UploadOverlay(attachment.upload)
            AltBadge(described, Modifier.align(Alignment.BottomStart).padding(AlohaSpacing.xs))
        }
        val failed = attachment.upload is UploadState.Failed || attachment.upload is UploadState.Refused
        if (failed) {
            FilledTonalIconButton(onClick = {
                actions.onRetryMedia(attachment.id)
            }, modifier = Modifier.align(Alignment.Center)) {
                Icon(AlohaIcons.Retry, stringResource(R.string.composer_media_retry))
            }
        }
        IconButton(onClick = { actions.onRemoveMedia(attachment.id) }, modifier = Modifier.align(Alignment.TopEnd)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = SCRIM)) {
                Icon(AlohaIcons.Close, stringResource(R.string.composer_media_remove, attachment.fileName))
            }
        }
    }
}

@Composable
private fun UploadOverlay(upload: UploadState) {
    val label = uploadLabel(upload) ?: return
    val fraction = (upload as? UploadState.Sending)?.fraction
    // only a change of state is said aloud; the percentage is there to ask for, not announced at every step
    val stage = stringResource(stageLabel(upload))
    val modifier = Modifier.size(PROGRESS).semantics {
        contentDescription = stage
        stateDescription = label
    }
    when (upload) {
        is UploadState.Sending, UploadState.Queued, UploadState.Processing -> if (fraction != null) {
            CircularProgressIndicator(progress = { fraction }, modifier = modifier)
        } else {
            CircularProgressIndicator(modifier)
        }

        else -> Unit
    }
}

/** ALT, filled when the attachment has a description and outlined when it has none. */
@Composable
private fun AltBadge(described: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.composer_media_alt),
        style = MaterialTheme.typography.labelSmall,
        color = if (described) colors.onPrimary else colors.onSurface,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .then(if (described) Modifier.background(colors.primary) else Modifier.background(colors.surface))
            .border(1.dp, if (described) colors.primary else colors.outline, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = AlohaSpacing.xs),
    )
}

@Composable
private fun uploadLabel(upload: UploadState): String? = when (upload) {
    UploadState.Queued -> stringResource(R.string.composer_media_waiting)

    is UploadState.Sending -> upload.fraction?.let {
        stringResource(R.string.composer_media_sending, (it * PERCENT).toInt())
    }
        ?: stringResource(R.string.composer_media_waiting)

    UploadState.Processing -> stringResource(R.string.composer_media_processing)

    is UploadState.Done -> null

    is UploadState.Refused -> upload.message?.let { stringResource(R.string.composer_media_refused, it) }
        ?: stringResource(R.string.composer_media_failed)

    UploadState.Failed -> stringResource(R.string.composer_media_failed)
}

/**
 * The description of one attachment and, for a picture, where its crop keeps in frame: a tap on the
 * picture moves the focal point there. A description is what a screen reader says, and what a remote
 * reader's app shows, in place of the picture. Closing the sheet keeps what was written, as Done
 * does: a description is never thrown away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaEditor(attachment: Attachment, onDone: (String, Focus?) -> Unit) {
    var description by remember(attachment.id) { mutableStateOf(attachment.description) }
    var focus by remember(attachment.id) { mutableStateOf(attachment.focus) }
    ModalBottomSheet(onDismissRequest = { onDone(description, focus) }) {
        Column(
            Modifier.padding(horizontal = AlohaSpacing.m).padding(bottom = AlohaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            Text(
                stringResource(R.string.composer_media_editor_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (attachment.isPicture) FocusPicker(attachment, focus) { focus = it }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(Attachments.DESCRIPTION_LIMIT) },
                label = { Text(stringResource(R.string.composer_media_description)) },
                supportingText = {
                    Text(
                        pluralStringResource(
                            R.plurals.composer_media_description_count,
                            Attachments.DESCRIPTION_LIMIT,
                            description.length,
                            Attachments.DESCRIPTION_LIMIT,
                        ),
                    )
                },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onDone(description, focus) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.composer_media_done))
            }
        }
    }
}

@Composable
private fun FocusPicker(attachment: Attachment, focus: Focus?, onFocus: (Focus) -> Unit) {
    val hint = stringResource(R.string.composer_media_focus_hint)
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        Box(Modifier.fillMaxWidth().heightIn(max = PREVIEW).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
            AsyncImage(
                attachment.file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { tap ->
                        // Mastodon's focus runs from −1 to 1 from the centre, with y pointing up
                        onFocus(Focus(tap.x / size.width * 2 - 1, 1 - tap.y / size.height * 2))
                    }
                },
            )
            focus?.let { point ->
                val ring = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val at = Offset((point.x + 1) / 2 * size.width, (1 - point.y) / 2 * size.height)
                    drawCircle(
                        Color.White,
                        radius = RING.toPx() + 2.dp.toPx(),
                        center = at,
                        style = Stroke(STROKE.toPx()),
                    )
                    drawCircle(ring, radius = RING.toPx(), center = at, style = Stroke(STROKE.toPx()))
                }
            }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val TILE = 96.dp
private val PROGRESS = 36.dp
private val PREVIEW = 320.dp
private val RING = 14.dp
private val STROKE = 3.dp
private const val PERCENT = 100
private const val SCRIM = 0.8f

/** What stage an upload is at, which is all a screen reader hears of it unasked. */
private fun stageLabel(upload: UploadState): Int = when (upload) {
    UploadState.Queued -> R.string.composer_media_stage_waiting
    UploadState.Processing -> R.string.composer_media_stage_processing
    else -> R.string.composer_media_stage_uploading
}

/** How far a focal point chosen by action sits from the middle. */
private const val EDGE = 0.8f
