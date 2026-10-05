// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.mediaviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.media.BlurHash
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Status
import social.aloha.core.ui.fadingBottom

/**
 * The backdrop for [attachment]: its blurhash's average colour, darkened to a lightness of at most
 * [BACKDROP_LIGHTNESS] so the picture stands out; black where it has none.
 */
internal fun backdropOf(attachment: MediaAttachment?): Color {
    val average = attachment?.blurhash?.let(BlurHash::averageColour) ?: return Color.Black
    val hsl = FloatArray(HSL)
    ColorUtils.colorToHSL(average, hsl)
    hsl[LIGHTNESS] = hsl[LIGHTNESS].coerceAtMost(BACKDROP_LIGHTNESS)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** The backdrop as the pager moves: the colour of the page it leaves blending into the one it reaches. */
internal fun backdropWhilePaging(pager: PagerState, attachments: List<MediaAttachment>): Color {
    val here = backdropOf(attachments.getOrNull(pager.currentPage))
    val fraction = pager.currentPageOffsetFraction
    if (fraction == 0f) return here
    val there = backdropOf(attachments.getOrNull(pager.currentPage + if (fraction > 0) 1 else -1))
    return lerp(here, there, abs(fraction))
}

/** The bars over the picture: the backdrop at nine tenths, so they read as part of it. */
internal fun barsOf(backdrop: Color): Color = backdrop.copy(alpha = BARS_ALPHA)

/** The description under the picture, four lines that fade into "more", then the whole of it. */
@Composable
internal fun Caption(description: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable(description) { mutableStateOf(false) }
    var overflows by remember(description) { mutableStateOf(false) }
    Column(modifier) {
        Text(
            description,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (open) Int.MAX_VALUE else CAPTION_LINES,
            onTextLayout = { if (!open) overflows = it.hasVisualOverflow },
            modifier = if (overflows && !open) Modifier.fadingBottom(FADE) else Modifier,
        )
        if (overflows || open) {
            TextButton(onClick = { open = !open }) {
                Text(stringResource(if (open) R.string.viewer_less else R.string.viewer_more_text), color = Color.White)
            }
        }
    }
}

/** Reply, boost, favourite and bookmark, for the post the pictures belong to; each toggle shows its state. */
@Composable
internal fun PostActions(status: Status, onReply: () -> Unit, onToggle: (Toggle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs)) {
        IconButton(onClick = onReply) {
            Icon(AlohaIcons.Reply, stringResource(R.string.viewer_reply), tint = Color.White)
        }
        IconButton(onClick = { onToggle(Toggle.Boost) }) {
            Icon(
                if (status.reblogged) AlohaIcons.Boosted else AlohaIcons.Boost,
                stringResource(if (status.reblogged) R.string.viewer_unboost else R.string.viewer_boost),
                tint = Color.White,
            )
        }
        IconButton(onClick = { onToggle(Toggle.Favourite) }) {
            Icon(
                if (status.favourited) AlohaIcons.Favourited else AlohaIcons.Favourite,
                stringResource(if (status.favourited) R.string.viewer_unfavourite else R.string.viewer_favourite),
                tint = Color.White,
            )
        }
        IconButton(onClick = { onToggle(Toggle.Bookmark) }) {
            Icon(
                if (status.bookmarked) AlohaIcons.Bookmarked else AlohaIcons.Bookmark,
                stringResource(if (status.bookmarked) R.string.viewer_unbookmark else R.string.viewer_bookmark),
                tint = Color.White,
            )
        }
    }
}

/** The bottom of the viewer: the caption over the post's actions, on the backdrop's bar colour. */
@Composable
internal fun BottomBar(
    caption: String?,
    status: Status?,
    actions: MediaViewerActions,
    trailing: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.s, vertical = AlohaSpacing.xs)) {
        caption?.let { Caption(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (status != null) PostActions(status, actions::onReply, actions::onToggle) else Row {}
            trailing()
        }
    }
}

private const val HSL = 3
private const val LIGHTNESS = 2
private const val BACKDROP_LIGHTNESS = 0.15f
private const val BARS_ALPHA = 0.9f
private const val CAPTION_LINES = 4
private val FADE = 24.dp
