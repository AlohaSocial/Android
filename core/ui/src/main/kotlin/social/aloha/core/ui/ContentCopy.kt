// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.content.ContentResolver
import android.net.Uri
import java.io.File

/** Copies what [uri] holds into [target]; false, and no file left, when it could not be read. */
public fun ContentResolver.copyTo(uri: Uri, target: File): Boolean {
    target.parentFile?.mkdirs()
    val copied = runCatching {
        openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
    }.getOrNull() != null
    if (!copied) target.delete()
    return copied
}

/** The file name extension for [mime], with its dot; none for a type without one known. */
public fun extensionFor(mime: String): String = when (mime) {
    "image/jpeg" -> ".jpg"
    "image/png" -> ".png"
    "image/gif" -> ".gif"
    "image/webp" -> ".webp"
    "image/heic", "image/heif" -> ".heic"
    "video/mp4" -> ".mp4"
    "video/quicktime" -> ".mov"
    else -> ""
}
