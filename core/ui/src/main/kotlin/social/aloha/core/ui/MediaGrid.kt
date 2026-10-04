// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.SensitiveMediaPolicy

/**
 * Laid out by count: one at its own shape within bounds, two side by side, three or four in a grid. What
 * is sensitive is covered while the policy says so, keeping its size, and is not even downloaded until
 * revealed; the cover shows the first attachment's blurhash, warning stripes and [wording], the author's
 * content warning where there is one. Reading's previewless mode lists the media as rows instead.
 */
@Composable
internal fun MediaGrid(
    media: List<MediaAttachment>,
    sensitive: Boolean,
    policy: SensitiveMediaPolicy,
    wording: String? = null,
    onOpen: (Int) -> Unit,
) {
    val covered = sensitive && !policy.allowsAutomaticReveal
    if (covered && !policy.drawsAtAll) {
        Text(
            stringResource(R.string.status_sensitive_hidden),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (LocalReadingStyle.current.previewless) return MediaRows(media, sensitive, onOpen)
    var revealed by rememberSaveable(media.firstOrNull()?.id) { mutableStateOf(false) }
    var describing by remember { mutableStateOf<String?>(null) }
    val hidden = covered && !revealed
    val shown = media.take(MAX_TILES)
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        when (shown.size) {
            1 -> Tile(shown[0], Modifier.fillMaxWidth().aspectRatio(aspectOf(shown[0])), hidden, { describing = it }) {
                onOpen(0)
            }

            else -> Grid(shown, hidden, { describing = it }, onOpen)
        }
        when {
            hidden -> MediaCover(shown.first(), wording, Modifier.matchParentSize()) { revealed = true }

            covered -> MediaBadge(stringResource(R.string.status_media_hide), Modifier.align(Alignment.TopEnd)) {
                revealed = false
            }
        }
    }
    describing?.let { DescriptionSheet(it) { describing = null } }
}

@Composable
private fun Grid(media: List<MediaAttachment>, hidden: Boolean, onAlt: (String) -> Unit, onOpen: (Int) -> Unit) {
    val rows = if (media.size == 2) listOf(media.indices.toList()) else media.indices.chunked(2)
    val aspect = if (media.size == 2) PAIR_ASPECT else 1f
    Column(verticalArrangement = Arrangement.spacedBy(GUTTER)) {
        rows.forEach { indices ->
            Row(horizontalArrangement = Arrangement.spacedBy(GUTTER)) {
                indices.forEach { index ->
                    Tile(media[index], Modifier.weight(1f).aspectRatio(aspect), hidden, onAlt) { onOpen(index) }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    attachment: MediaAttachment,
    modifier: Modifier,
    hidden: Boolean,
    onAlt: (String) -> Unit,
    onOpen: () -> Unit,
) {
    Box(modifier.clickable(enabled = !hidden, onClick = onOpen)) {
        if (hidden) {
            // the cover shows the blurhash only: nothing is fetched from a sensitive post until it is revealed
            Blurhash(attachment, Modifier.matchParentSize())
        } else {
            MediaImage(attachment, attachment.description, Modifier.matchParentSize(), fitToAspect = false)
            if (attachment.type.isPlayable && attachment.type != AttachmentKind.Audio) {
                PlayBadge(Modifier.align(Alignment.Center))
            }
            val description = attachment.description
            val corner = Modifier.align(Alignment.BottomStart)
            if (!description.isNullOrBlank()) {
                MediaBadge(stringResource(R.string.status_alt_badge), corner) { onAlt(description) }
            } else if (LocalReadingStyle.current.missingAltBadge) {
                MediaBadge(stringResource(R.string.status_media_no_alt), corner)
            }
        }
    }
}

@Composable
private fun Blurhash(attachment: MediaAttachment, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        rememberBlurHashPainter(attachment.blurhash)?.let {
            Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** Over covered media: the first attachment's blurhash, the warning stripes, and what it is. */
@Composable
private fun MediaCover(first: MediaAttachment, wording: String?, modifier: Modifier, onReveal: () -> Unit) {
    Box(modifier.clickable(onClick = onReveal)) {
        Blurhash(first, Modifier.matchParentSize())
        Column(
            Modifier.matchParentSize().background(Color.Black.copy(alpha = COVER_ALPHA)).hiddenEdges(HiddenEdge.Warning)
                .padding(horizontal = EDGE + AlohaSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AlohaIcons.Sensitive, contentDescription = null, tint = Color.White)
            Text(
                wording?.takeIf { it.isNotBlank() } ?: stringResource(R.string.status_sensitive_cover),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                stringResource(R.string.status_sensitive_show),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}

/** A small dark pill on media: ALT (opens the description), "no ALT", or Hide. */
@Composable
private fun MediaBadge(text: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.padding(AlohaSpacing.xs),
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Black.copy(alpha = COVER_ALPHA),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = AlohaSpacing.xxs),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DescriptionSheet(description: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = AlohaSpacing.m, end = AlohaSpacing.m, bottom = AlohaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            Text(
                stringResource(R.string.status_media_description),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(description, style = MaterialTheme.typography.bodyLarge.contentDirection())
        }
    }
}

/** Previewless: each attachment a row with its kind, its description and whether it is sensitive. */
@Composable
private fun MediaRows(media: List<MediaAttachment>, sensitive: Boolean, onOpen: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs)) {
        media.forEachIndexed { index, attachment ->
            val (icon, kind) = kindOf(attachment.type)
            Surface(
                onClick = { onOpen(index) },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(AlohaSpacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(ROW_ICON))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(kind), style = MaterialTheme.typography.labelLarge)
                        Text(
                            attachment.description?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.status_media_no_description),
                            style = MaterialTheme.typography.bodySmall.contentDirection(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    if (sensitive) {
                        Text(
                            stringResource(R.string.status_media_sensitive),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private fun aspectOf(single: MediaAttachment) = single.displayAspectRatio.toFloat().coerceIn(MIN_ASPECT, MAX_ASPECT)

private fun kindOf(type: AttachmentKind): Pair<ImageVector, Int> = when (type) {
    AttachmentKind.Video, AttachmentKind.Gifv -> AlohaIcons.Video to R.string.status_media_video
    AttachmentKind.Audio -> AlohaIcons.Audio to R.string.status_media_audio
    AttachmentKind.Image -> AlohaIcons.Photos to R.string.status_media_picture
    AttachmentKind.UnsupportedFile, AttachmentKind.Unknown -> AlohaIcons.Photos to R.string.status_media_file
}

private const val MAX_TILES = 4
private const val MIN_ASPECT = 0.8f
private const val MAX_ASPECT = 1.91f
private const val PAIR_ASPECT = 0.9f
internal const val COVER_ALPHA = 0.55f
private val GUTTER = 2.dp
private val ROW_ICON = 24.dp
