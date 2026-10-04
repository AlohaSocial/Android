// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import social.aloha.core.model.LogArea
import social.aloha.core.model.ServerLimits
import social.aloha.core.ui.copyTo
import social.aloha.core.ui.extensionFor
import timber.log.Timber

/** What the server's limits make of a file before it is sent. */
internal sealed interface Preflight {
    data object Fits : Preflight

    /** A picture the server takes only smaller; it is made smaller rather than refused. */
    data object ShrinkPicture : Preflight

    /** A type the server does not accept; HEIC and HEIF become JPEG instead, the rest is refused. */
    data class Unsupported(val mimeType: String) : Preflight

    /** A video the server takes only as MP4, which it is converted into. */
    data object ConvertVideo : Preflight

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

    fun check(mimeType: String, size: Long, limits: ServerLimits): Preflight =
        type(mimeType, limits) ?: size(mimeType, size, limits)

    /** What the server makes of the type: null when it takes it as it is. */
    private fun type(mimeType: String, limits: ServerLimits): Preflight? {
        val types = limits.supportedMimeTypes
        val mp4 = types.isEmpty() || "video/mp4" in types
        return when {
            types.isEmpty() || mimeType in types -> null
            mimeType.startsWith("video/") && mp4 -> Preflight.ConvertVideo
            mimeType in convertible -> Preflight.ShrinkPicture
            else -> Preflight.Unsupported(mimeType)
        }
    }

    /** What the server makes of the size, each kind against its own ceiling. */
    private fun size(mimeType: String, size: Long, limits: ServerLimits): Preflight = when {
        // a moving picture would come back as its first frame: refused rather than flattened
        mimeType in moving && size > limits.imageSizeLimit -> Preflight.TooLarge(limits.imageSizeLimit)

        mimeType.startsWith("image/") && size > limits.imageSizeLimit -> Preflight.ShrinkPicture

        !mimeType.startsWith("image/") && size > limits.videoSizeLimit -> Preflight.TooLarge(limits.videoSizeLimit)

        else -> Preflight.Fits
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
internal class MediaPreparation(
    private val resolver: ContentResolver,
    private val directory: File,
    private val videos: VideoTransformer,
    /** The app's own camera captures, which are let go of once copied. */
    private val captures: String,
) {
    fun copy(uri: Uri): Picked? {
        val mime = resolver.getType(uri) ?: return null
        val name = displayName(uri) ?: "attachment"
        val target = newFile(extensionFor(mime))
        if (!resolver.copyTo(uri, target)) return null
        // a photo or video just taken is kept once, here, not twice
        if (uri.authority == captures) runCatching { resolver.delete(uri, null, null) }
        return Picked(target, name, mime)
    }

    /**
     * [picked] as the server will take it, or why it will not: the file as it now is (scaled, or
     * converted to MP4) with the verdict. A file this gives up on, when it made another, is deleted.
     */
    suspend fun prepare(picked: Picked, limits: ServerLimits): Prepared =
        when (val check = UploadPreflight.check(picked.mimeType, picked.file.length(), limits)) {
            Preflight.ShrinkPicture -> withContext(Dispatchers.IO) { shrink(picked, limits) }
                ?.let { Prepared(it, Preflight.Fits) } ?: Prepared(picked, check)

            // converted, it is checked again: an MP4 can still be over the ceiling
            Preflight.ConvertVideo -> video(picked, null)
                ?.let { converted ->
                    picked.file.delete()
                    prepare(converted, limits)
                }
                ?: Prepared(picked, Preflight.Unsupported(picked.mimeType))

            else -> Prepared(picked, check)
        }

    /** [picked] as an MP4, trimmed and scaled by [edit]; null when it could not be made. */
    suspend fun video(picked: Picked, edit: VideoEdit?): Picked? {
        val target = newFile(".mp4")
        if (!videos.export(picked.file, edit, target)) return null
        return Picked(target, rename(picked.fileName, "video/mp4"), "video/mp4")
    }

    /**
     * Scales [picked] down, a quarter at a time, until it is under the ceiling; null when it cannot be.
     * Decoded at most [MAX_EDGE] across, as no server keeps more, so a 50-megapixel photo never sits
     * in memory whole.
     */
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

    /**
     * [original] drawn through [filter] into a new file, or null for a picture a filter cannot touch:
     * a GIF or animated WebP would come back as its first frame. Never throws; a filter is a
     * decoration, and losing an upload over one is not worth it.
     */
    fun filtered(original: Picked, filter: PhotoFilter): Picked? {
        if (original.mimeType in UNFILTERABLE) return null
        val source = decode(original.file) ?: return null
        val png = original.mimeType == "image/png"
        val mime = if (png) "image/png" else "image/jpeg"
        val target = newFile(extensionFor(mime))
        var drawn: Bitmap? = null
        return runCatching {
            val canvas = createBitmap(source.width, source.height).also { drawn = it }
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.androidMatrix)) }
            Canvas(canvas).drawBitmap(source, 0f, 0f, paint)
            write(canvas, png, target)
            Picked(target, rename(original.fileName, mime), mime)
        }.getOrElse {
            Timber.tag(LogArea.Media.name).w("Filter not applied: %s", it.javaClass.simpleName)
            target.delete()
            null
        }.also {
            source.recycle()
            drawn?.recycle()
        }
    }

    private fun newFile(extension: String): File {
        directory.mkdirs()
        return File(directory, UUID.randomUUID().toString() + extension)
    }

    private fun write(bitmap: Bitmap, png: Boolean, target: File) = target.outputStream().use { out ->
        bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, QUALITY, out)
    }

    /** [file] decoded in ordinary memory (a hardware bitmap cannot be scaled), at most [MAX_EDGE] across. */
    private fun decode(file: File): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val edge = maxOf(info.size.width, info.size.height)
                if (edge > MAX_EDGE) {
                    decoder.setTargetSize(info.size.width * MAX_EDGE / edge, info.size.height * MAX_EDGE / edge)
                }
            }
        }.onFailure { Timber.tag(LogArea.Media.name).w("Photo not decoded: %s", it.javaClass.simpleName) }.getOrNull()
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

    private fun rename(name: String, mime: String): String = name.substringBeforeLast('.') + extensionFor(mime)

    companion object {
        /** Moving pictures, which a filter would flatten to their first frame. */
        val UNFILTERABLE = setOf("image/gif", "image/webp")
        private const val MAX_EDGE = 4_096
        private const val QUALITY = 92
        private const val STEP = 0.75
        private const val ATTEMPTS = 6
    }
}
