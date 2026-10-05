// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.sync.UploadState
import social.aloha.core.ui.longPressDrag
import social.aloha.core.ui.motion
import social.aloha.core.ui.moves
import social.aloha.core.ui.rememberReordering
import social.aloha.core.ui.reorderItem

/**
 * The attachments of one post, a card each, which opens its description and focal point; a long press drags
 * a card to another place, and a screen reader moves it with the card's actions.
 */
@Composable
internal fun MediaStrip(attachments: List<Attachment>, actions: ComposerActions, drafts: Boolean = false) {
    val reordering = rememberReordering(attachments, key = { it.id }, gap = AlohaSpacing.s) { order ->
        actions.onOrderMedia(order.map { it.id })
    }
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        reordering.order.forEachIndexed { index, attachment ->
            key(attachment.id) {
                MediaCard(
                    attachment,
                    actions,
                    drafts && attachment.isPicture && attachment.source != null,
                    reordering.moves(index),
                    Modifier.reorderItem(reordering, attachment).longPressDrag(reordering, attachment),
                )
            }
        }
    }
}

/**
 * One attachment: its picture, and in its title line how it stands, uploading, processing, its description,
 * or a call for one in the error colour. A failed upload turns the card to the error colours, with a retry.
 */
@Composable
private fun MediaCard(
    attachment: Attachment,
    actions: ComposerActions,
    draftable: Boolean,
    moves: List<CustomAccessibilityAction>,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val failed = attachment.upload is UploadState.Failed || attachment.upload is UploadState.Refused
    val container by animateColorAsState(
        if (failed) colors.errorContainer else colors.surfaceContainerHigh,
        motion(tween()),
        label = "card",
    )
    val content = if (failed) colors.onErrorContainer else colors.onSurface
    val status = uploadLabel(attachment)
    val described = attachment.description.isNotBlank()
    val summary = listOfNotNull(
        attachment.fileName,
        status,
        stringResource(if (described) R.string.composer_media_described else R.string.composer_media_undescribed),
    ).joinToString(", ")
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.composer_media_edit), role = Role.Button) {
                    actions.onEditMedia(attachment.id)
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = summary
                    customActions = moves
                }
                .padding(AlohaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            MediaThumb(attachment)
            CardText(attachment, status, failed, Modifier.weight(1f))
            if (failed) {
                TextButton(onClick = { actions.onRetryMedia(attachment.id) }) {
                    Text(stringResource(R.string.composer_media_retry))
                }
            }
            // where the call for a description is, a way to have one drafted
            if (draftable && !described && !failed) {
                IconButton(onClick = { actions.onDraftAlt(attachment.id) }) {
                    Icon(AlohaIcons.Intelligence, stringResource(R.string.composer_alt_draft_for, attachment.fileName))
                }
            }
            IconButton(onClick = { actions.onRemoveMedia(attachment.id) }) {
                Icon(AlohaIcons.Close, stringResource(R.string.composer_media_remove, attachment.fileName))
            }
        }
    }
}

/** The card's title line, how the attachment stands, and its file's name under it. */
@Composable
private fun CardText(attachment: Attachment, status: String?, failed: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val described = attachment.description.isNotBlank()
    Column(modifier) {
        Text(
            status ?: attachment.description.takeIf { described } ?: stringResource(R.string.composer_media_add_alt),
            style = MaterialTheme.typography.titleSmall,
            color = if (status == null && !described && !failed) colors.error else LocalContentColor.current,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            attachment.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) LocalContentColor.current else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The attachment's picture, or for a file its name, with how far its upload is over it. */
@Composable
private fun MediaThumb(attachment: Attachment) {
    Box(
        Modifier.size(
            THUMB,
        ).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.isPicture) {
            AsyncImage(
                attachment.file ?: attachment.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(AlohaIcons.AttachFile, contentDescription = null)
        }
        when {
            attachment.preparing -> CircularProgressIndicator(Modifier.size(PROGRESS))
            attachment.oversizedLimit == null -> UploadOverlay(attachment.upload)
            else -> Icon(AlohaIcons.Trim, contentDescription = null)
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

@Composable
private fun uploadLabel(attachment: Attachment): String? = when {
    attachment.preparing -> stringResource(R.string.composer_media_preparing)

    attachment.oversizedLimit != null ->
        stringResource(
            R.string.composer_media_oversized,
            stringResource(R.string.composer_size_mb, Attachments.megabytes(attachment.oversizedLimit)),
        )

    else -> uploadLabel(attachment.upload)
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

/** What stage an upload is at, which is all a screen reader hears of it unasked. */
private fun stageLabel(upload: UploadState): Int = when (upload) {
    UploadState.Queued -> R.string.composer_media_stage_waiting
    UploadState.Processing -> R.string.composer_media_stage_processing
    else -> R.string.composer_media_stage_uploading
}

private val THUMB = 64.dp
private const val PERCENT = 100
private val PROGRESS = 36.dp
