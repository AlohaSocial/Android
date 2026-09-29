// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import social.aloha.core.media.BlurHash
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.MediaFocus

/**
 * An attachment's still image: the blurhash painted at once in a box already the image's shape, so the
 * row never shifts, then the preview cropped around the focal point the author set. [contentDescription]
 * is the author's alt text; a missing one is null here, and the row says the image has none.
 */
@Composable
public fun MediaImage(
    attachment: MediaAttachment,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fitToAspect: Boolean = true,
) {
    val placeholder = rememberBlurHashPainter(attachment.blurhash)
    val box = if (fitToAspect) modifier.aspectRatio(attachment.displayAspectRatio.toFloat()) else modifier
    Box(box.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = attachment.previewUrl ?: attachment.url,
            contentDescription = contentDescription,
            placeholder = placeholder,
            error = placeholder,
            contentScale = ContentScale.Crop,
            alignment = focalAlignment(attachment.meta?.focus),
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * Where to anchor a crop so the author's focal point stays in view. Mastodon's focus runs from −1 to 1
 * with y pointing up; Compose's bias runs the same way horizontally and downwards vertically.
 */
public fun focalAlignment(focus: MediaFocus?): Alignment =
    focus?.let { BiasAlignment(it.x.toFloat().coerceIn(-1f, 1f), (-it.y).toFloat().coerceIn(-1f, 1f)) }
        ?: Alignment.Center

/**
 * The blurhash as a small bitmap, scaled up by the image it stands in for; the average colour where it
 * cannot be decoded.
 */
@Composable
public fun rememberBlurHashPainter(hash: String?): Painter? = remember(hash) {
    hash ?: return@remember null
    BlurHash.decode(hash, PLACEHOLDER_SIZE, PLACEHOLDER_SIZE)?.let { pixels ->
        val bitmap = Bitmap.createBitmap(pixels, PLACEHOLDER_SIZE, PLACEHOLDER_SIZE, Bitmap.Config.ARGB_8888)
        BitmapPainter(bitmap.asImageBitmap())
    } ?: BlurHash.averageColour(hash)?.let { ColorPainter(Color(it)) }
}

private const val PLACEHOLDER_SIZE = 32
