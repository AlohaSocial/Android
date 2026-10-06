// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * [file] decoded in ordinary memory (a hardware bitmap cannot be scaled or read), at most [maxEdge]
 * across, so a camera's full size never sits in memory; null when it is no picture this device decodes.
 */
public fun decodeScaled(file: File, maxEdge: Int): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val edge = maxOf(info.size.width, info.size.height)
            if (edge > maxEdge) {
                decoder.setTargetSize(info.size.width * maxEdge / edge, info.size.height * maxEdge / edge)
            }
        }
    } catch (_: IOException) {
        null
    }
} else {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
