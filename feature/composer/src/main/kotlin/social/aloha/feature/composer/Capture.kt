// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** What the camera is asked for: a photo, a video, or a short of a set length in seconds. */
internal sealed interface Capture {
    data object Photo : Capture

    data object Video : Capture

    data class Short(val seconds: Int) : Capture

    companion object {
        /** The lengths a short is offered in. */
        val SHORT_SECONDS = listOf(15, 30, 60)
    }
}

/**
 * The system camera recording a video, stopped after [seconds] when set: the camera app's own
 * duration limit, so a short needs nothing of its own to stop on time.
 */
internal class CaptureVideo : ActivityResultContracts.CaptureVideo() {
    var seconds: Int? = null

    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply { seconds?.let { putExtra(MediaStore.EXTRA_DURATION_LIMIT, it) } }
}

/**
 * Where the camera writes what it captures: a file in the app's cache, shared with the camera app
 * through this module's file provider and read back like anything picked.
 */
internal object CaptureFiles {
    fun target(context: Context, extension: String): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, UUID.randomUUID().toString() + extension)
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    private const val DIRECTORY = "captures"

    /** The provider the camera writes through, whose files are the app's own. */
    fun authority(context: Context): String = context.packageName + AUTHORITY

    /** Lets go of a capture that was never taken. */
    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private const val AUTHORITY = ".composer.captures"
}
