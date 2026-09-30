// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import java.io.File
import java.util.UUID
import social.aloha.core.model.ServerLimits
import social.aloha.core.ui.copyTo
import social.aloha.core.ui.extensionFor

/** What the server's limits make of a file before it is sent. */
internal sealed interface Preflight {
    data object Fits : Preflight

    /** A picture the server takes only smaller; it is made smaller rather than refused. */
    data object ShrinkPicture : Preflight

    /** A type the server does not accept; HEIC and HEIF become JPEG instead, the rest is refused. */
    data class Unsupported(val mimeType: String) : Preflight

    /** Over the server's ceiling for its kind, which is stated so the writer knows what fits. */
    data class TooLarge(val limitBytes: Long) : Preflight
}

/**
 * The checks the server would make, made before anything is sent: the type among the server's
 * `supported_mime_types` (a server that lists none takes anything), and the size under the ceiling for
 * its kind. A picture is read whole into the server's memory and a video a chunk at a time, so each
 * has its own ceiling.
 */
internal object UploadPreflight {
    private val convertible = setOf("image/heic", "image/heif")
    private val moving = setOf("image/gif", "image/webp")

    fun check(mimeType: String, size: Long, limits: ServerLimits): Preflight {
        val accepted = limits.supportedMimeTypes.isEmpty() || mimeType in limits.supportedMimeTypes
        val picture = mimeType.startsWith("image/")
        return when {
            !accepted && mimeType !in convertible -> Preflight.Unsupported(mimeType)

            !accepted -> Preflight.ShrinkPicture

            // a moving picture would come back as its first frame: refused rather than flattened
            mimeType in moving && size > limits.imageSizeLimit -> Preflight.TooLarge(limits.imageSizeLimit)

            picture && size > limits.imageSizeLimit -> Preflight.ShrinkPicture

            !picture && size > limits.videoSizeLimit -> Preflight.TooLarge(limits.videoSizeLimit)

            else -> Preflight.Fits
        }
    }
}

/** A file the writer picked, copied into the app so an upload outlives the picker's permission. */
internal data class Picked(val file: File, val fileName: String, val mimeType: String)

/** What preparing a file ended with: the file as it now is, and whether the server takes it. */
internal data class Prepared(val picked: Picked, val check: Preflight)

/**
 * Copies what the writer picks into the app's own storage and makes pictures fit: HEIC and HEIF
 * become JPEG where the server does not take them, and a picture over the server's ceiling is
 * scaled down until it fits. JPEG at 0.92; a PNG stays PNG so a screenshot keeps its transparency.
 */
internal class MediaPreparation(private val resolver: ContentResolver, private val directory: File) {
    fun copy(uri: Uri): Picked? {
        val mime = resolver.getType(uri) ?: return null
        val name = displayName(uri) ?: "attachment"
        val target = newFile(extensionFor(mime))
        if (!resolver.copyTo(uri, target)) return null
        // a photo or video just taken is kept once, here, not twice
        if (uri.authority == captures) runCatching { resolver.delete(uri, null, null) }
        return Picked(target, name, mime)
    }

    /** [picked] as the server will take it, or why it will not: the file to send, or the refusal. */
    fun prepare(picked: Picked, limits: ServerLimits): Pair<Picked?, Preflight> =
        when (val check = UploadPreflight.check(picked.mimeType, picked.file.length(), limits)) {
            Preflight.Fits -> picked to check
            Preflight.ShrinkPicture -> shrink(picked, limits)?.let { it to Preflight.Fits } ?: (null to check)
            else -> null to check
        }

    /** Scales [picked] down, a quarter at a time, until it is under the ceiling; null when it cannot be. */
    private fun shrink(picked: Picked, limits: ServerLimits): Picked? {
        val bitmap = decode(picked.file) ?: return null
        val png = picked.mimeType == "image/png" && "image/png" in limits.supportedMimeTypes
        val mime = if (png) "image/png" else "image/jpeg"
        val target = newFile(extensionFor(mime))
        var scale = 1.0
        try {
            repeat(ATTEMPTS) {
                val scaled = if (scale == 1.0) {
                    bitmap
                } else {
                    bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
                }
                write(scaled, png, target)
                if (scaled !== bitmap) scaled.recycle()
                if (target.length() <= limits.imageSizeLimit) {
                    picked.file.delete()
                    return Picked(target, rename(picked.fileName, mime), mime)
                }
                scale *= STEP
            }
        } finally {
            bitmap.recycle()
        }
        target.delete()
        return null
    }

    private fun decode(file: File): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // in ordinary memory: a hardware bitmap cannot be scaled
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val edge = maxOf(info.size.width, info.size.height)
                if (edge > MAX_EDGE) {
                    decoder.setTargetSize(info.size.width * MAX_EDGE / edge, info.size.height * MAX_EDGE / edge)
                }
            }
        }.getOrNull()
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun extension(mime: String): String = when (mime) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/heic", "image/heif" -> ".heic"
        "video/mp4" -> ".mp4"
        "video/quicktime" -> ".mov"
        else -> ""
    }

    private fun rename(name: String, mime: String): String = name.substringBeforeLast('.') + extension(mime)

    private companion object {
        const val QUALITY = 92
        const val STEP = 0.75
        const val ATTEMPTS = 6
    }
}
