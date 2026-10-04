// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.VideoChapters

/**
 * A video as Video mode lists it: its poster frame at 16:9 with the length on it and, where the reader
 * started it, how far they got; then its title, who posted it and how often it was watched. A sensitive
 * video shows its blur alone. The card reads aloud as one button.
 *
 * @param watched how far through the reader got, between 0 and 1; null for a video not started.
 */
@Composable
public fun VideoCard(row: StatusRowUi, watched: Double?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val video = row.media.firstOrNull { it.type.isVideo } ?: row.media.firstOrNull()
    val length = (video?.meta?.original?.duration ?: row.video?.duration)?.let(VideoChapters::clock)
    val title = videoTitle(row)
    val label = listOfNotNull(
        title,
        row.author.plainName,
        length?.let { stringResource(R.string.video_length, it) },
        viewsLabel(row),
        watched?.let { stringResource(R.string.video_watched, (it * PERCENT).toInt()) },
        stringResource(R.string.video_sensitive).takeIf { row.sensitive },
    ).joinToString(", ")
    Column(
        modifier.fillMaxWidth().clickable(onClick = onOpen).clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
        }.padding(AlohaSpacing.s),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(WIDE).clip(RoundedCornerShape(CORNER))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Poster(video, row.sensitive)
            length?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(AlohaSpacing.xs)
                        .background(Color.Black.copy(alpha = SCRIM), RoundedCornerShape(BADGE_CORNER))
                        .padding(horizontal = AlohaSpacing.xs),
                )
            }
            watched?.let {
                LinearProgressIndicator(
                    progress = { it.toFloat() },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(BAR),
                    strokeCap = StrokeCap.Butt,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val byline = listOfNotNull(row.author.plainName, viewsLabel(row)).joinToString(" · ")
        Text(
            byline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Poster(video: MediaAttachment?, sensitive: Boolean) {
    val fill = Modifier.fillMaxSize()
    when {
        video == null -> Unit

        sensitive -> {
            rememberBlurHashPainter(video.blurhash)?.let { Image(it, null, fill, contentScale = ContentScale.Crop) }
            Box(fill, contentAlignment = Alignment.Center) { Icon(AlohaIcons.Sensitive, contentDescription = null) }
        }

        else -> AsyncImage(
            model = video.previewUrl,
            contentDescription = null,
            placeholder = rememberBlurHashPainter(video.blurhash),
            contentScale = ContentScale.Crop,
            modifier = fill,
        )
    }
}

/** What a video is called: the title the server knows, else its content warning, else its first line. */
@Composable
public fun videoTitle(row: StatusRowUi): String = row.video?.title
    ?: row.spoiler?.text?.takeIf { it.isNotBlank() }
    ?: row.plainText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    ?: stringResource(R.string.video_untitled)

/** "12 views", or null for none, or while the reader hides the numbers. */
@Composable
private fun viewsLabel(row: StatusRowUi): String? = row.video?.views
    ?.takeIf { it > 0 && LocalReadingStyle.current.showCounts }
    ?.let { pluralStringResource(R.plurals.video_views, it, it) }

private val AttachmentKind.isVideo: Boolean get() = this == AttachmentKind.Video || this == AttachmentKind.Gifv

private const val WIDE = 16f / 9f
private const val SCRIM = 0.7f
private const val PERCENT = 100
private val CORNER = 12.dp
private val BADGE_CORNER = 4.dp
private val BAR = 4.dp
