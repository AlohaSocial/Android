// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import java.util.concurrent.TimeUnit
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
    val status = uploadLabel(attachment)
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
            if (attachment.preparing) {
                CircularProgressIndicator(Modifier.size(PROGRESS))
            } else if (attachment.oversizedLimit == null) {
                UploadOverlay(attachment.upload)
            } else {
                Icon(AlohaIcons.Trim, contentDescription = null)
            }
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
private fun uploadLabel(attachment: Attachment): String? = when {
    attachment.preparing -> stringResource(R.string.composer_media_preparing)

    attachment.oversizedLimit != null ->
        stringResource(R.string.composer_media_oversized, Attachments.size(attachment.oversizedLimit))

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

/** What the editor asks to change beyond the words: a picture's filter, or a video's trim and size. */
internal sealed interface MediaChange {
    data class Filter(val filter: PhotoFilter) : MediaChange

    data class Video(val edit: VideoEdit) : MediaChange
}

/**
 * The description of one attachment and, for a picture, where its crop keeps in frame: a tap on the
 * picture moves the focal point there. A description is what a screen reader says, and what a remote
 * reader's app shows, in place of the picture. Closing the sheet keeps what was written, as Done
 * does: a description is never thrown away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaEditor(attachment: Attachment, video: VideoInfo?, onDone: (String, Focus?, MediaChange?) -> Unit) {
    var description by remember(attachment.id) { mutableStateOf(attachment.description) }
    var focus by remember(attachment.id) { mutableStateOf(attachment.focus) }
    var filter by remember(attachment.id) { mutableStateOf(attachment.filter) }
    var cut by remember(attachment.id, video) { mutableStateOf(video?.let(VideoEdit::whole)) }
    val finish = {
        val change = when {
            filter != attachment.filter -> MediaChange.Filter(filter)

            // an oversized video is edited whatever was touched; another only when it was
            video != null && cut != null && (cut != VideoEdit.whole(video) || attachment.oversizedLimit != null) ->
                MediaChange.Video(checkNotNull(cut))

            else -> null
        }
        onDone(description, focus, change)
    }
    ModalBottomSheet(onDismissRequest = finish) {
        Column(
            Modifier.padding(horizontal = AlohaSpacing.m).padding(bottom = AlohaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            Text(
                stringResource(R.string.composer_media_editor_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (attachment.isPicture) FocusPicker(attachment, focus, filter) { focus = it }
            if (attachment.filterable) FilterRow(attachment, filter) { filter = it }
            if (video != null) cut?.let { VideoTrim(attachment, video, it) { changed -> cut = changed } }
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
            Button(onClick = finish, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.composer_media_done))
            }
        }
    }
}

@Composable
private fun FocusPicker(attachment: Attachment, focus: Focus?, filter: PhotoFilter, onFocus: (Focus) -> Unit) {
    val hint = stringResource(R.string.composer_media_focus_hint)
    // a tap sets the point; a screen reader or a switch picks it from the middle and the edges
    val points = listOf(
        R.string.composer_media_focus_centre to Focus(0f, 0f),
        R.string.composer_media_focus_top to Focus(0f, EDGE),
        R.string.composer_media_focus_bottom to Focus(0f, -EDGE),
        R.string.composer_media_focus_left to Focus(-EDGE, 0f),
        R.string.composer_media_focus_right to Focus(EDGE, 0f),
    ).map { (label, point) -> stringResource(label) to point }
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        Box(Modifier.fillMaxWidth().heightIn(max = PREVIEW).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
            // the choice shows over the picture as picked, so trying filters costs nothing
            AsyncImage(
                attachment.original ?: attachment.file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = filter.preview(),
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

/**
 * The card as it will be posted, drawn by the same code as the picture that is uploaded, and its six
 * backgrounds; the words themselves stay in the box above, so the picture says nothing new aloud.
 */
@Composable
internal fun CardPreview(card: CardUi, actions: ComposerActions) {
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        card.preview?.let { preview ->
            Image(
                preview,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = PREVIEW).aspectRatio(1f, true)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
            card.backgrounds.forEachIndexed { index, colour ->
                val selected = index == card.background
                val name = stringResource(R.string.composer_card_background, index + 1)
                Box(
                    Modifier
                        .size(SWATCH_TARGET)
                        .selectable(selected, role = Role.RadioButton) { actions.onCardBackground(index) }
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(SWATCH)
                            .clip(CircleShape)
                            .background(Color(colour))
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * The part of a video to keep, and the size to make it, with how large the result comes to against
 * the server's ceiling; the size choices offered are only those smaller than the video already is.
 */
@Composable
private fun VideoTrim(attachment: Attachment, video: VideoInfo, edit: VideoEdit, onEdit: (VideoEdit) -> Unit) {
    val source = (attachment.original ?: attachment.file).length()
    val estimate = edit.estimate(source, video)
    val limit = attachment.oversizedLimit
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        Text(stringResource(R.string.composer_video_trim), style = MaterialTheme.typography.titleSmall)
        val kept = stringResource(R.string.composer_video_kept, seconds(edit.startMs), seconds(edit.endMs))
        RangeSlider(
            modifier = Modifier.semantics { stateDescription = kept },
            value = edit.startMs.toFloat()..edit.endMs.toFloat(),
            onValueChange = { range ->
                onEdit(edit.copy(startMs = range.start.toLong(), endMs = range.endInclusive.toLong()))
            },
            valueRange = 0f..video.durationMs.toFloat(),
        )
        Text(kept, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            val sizes = listOf<Int?>(null) + VideoEdit.SIZES.filter { it < video.shortSide }
            sizes.forEach { side ->
                FilterChip(
                    selected = edit.shortSide == side,
                    onClick = { onEdit(edit.copy(shortSide = side)) },
                    label = {
                        Text(
                            side?.let { stringResource(R.string.composer_video_size, it) }
                                ?: stringResource(R.string.composer_video_original),
                        )
                    },
                )
            }
        }
        val over = limit != null && estimate > limit
        // over the limit is said in words, not only in the error colour
        Text(
            stringResource(
                if (over) R.string.composer_video_estimate_over else R.string.composer_video_estimate,
                Attachments.size(estimate),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** [ms] as a clock reads a length: "1:05". */
private fun seconds(ms: Long): String = DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(ms))

/** The eight filters over the picture as it was picked, each named, the chosen one marked. */
@Composable
private fun FilterRow(attachment: Attachment, chosen: PhotoFilter, onFilter: (PhotoFilter) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        modifier = Modifier.selectableGroup(),
    ) {
        items(PhotoFilter.entries, key = { it.name }) { filter ->
            val selected = filter == chosen
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .selectable(selected, role = Role.RadioButton) { onFilter(filter) }
                    .padding(AlohaSpacing.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    attachment.original ?: attachment.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter.preview(),
                    modifier = Modifier
                        .size(FILTER_TILE)
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                            } else {
                                Modifier
                            },
                        ),
                )
                Text(stringResource(filter.label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun PhotoFilter.preview(): ColorFilter? =
    if (this == PhotoFilter.Original) null else ColorFilter.colorMatrix(ColorMatrix(androidMatrix))

private val PhotoFilter.label: Int
    get() = when (this) {
        PhotoFilter.Original -> R.string.composer_filter_original
        PhotoFilter.Mono -> R.string.composer_filter_mono
        PhotoFilter.Noir -> R.string.composer_filter_noir
        PhotoFilter.Warm -> R.string.composer_filter_warm
        PhotoFilter.Cool -> R.string.composer_filter_cool
        PhotoFilter.Vivid -> R.string.composer_filter_vivid
        PhotoFilter.Faded -> R.string.composer_filter_faded
        PhotoFilter.Sepia -> R.string.composer_filter_sepia
    }

private val FILTER_TILE = 64.dp
private val SWATCH = 32.dp
private val SWATCH_TARGET = 48.dp
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
