// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import social.aloha.core.ui.isForeignContent

/** Whether a clip offers pictures or videos, which the composer attaches. */
internal fun carriesMedia(description: ClipDescription): Boolean = (0 until description.mimeTypeCount).any {
    val type = description.getMimeType(it)
    type.startsWith("image/") || type.startsWith("video/")
}

/** The pictures and videos in [clip] that came from other apps; the app's own files never. */
internal fun mediaIn(clip: ClipData, context: Context): List<Uri> {
    if (!carriesMedia(clip.description)) return emptyList()
    return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        .filter { isForeignContent(it, context.packageName) }
}

/** The pictures and videos on the clipboard, for Paste. */
internal fun clipboardMedia(context: Context): List<Uri> =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.let { mediaIn(it, context) }.orEmpty()

/**
 * Where pictures and videos dragged in from another app land, as from a gallery beside the composer
 * on a tablet or a desktop window; [onDrop] attaches them.
 */
@Composable
internal fun rememberMediaDrop(onDrop: (List<Uri>) -> Unit): DragAndDropTarget {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val attach by rememberUpdatedState(onDrop)
    return remember(activity, context) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val drag = event.toAndroidDragEvent()
                // another app's content is readable only once the drop's permissions are asked for; they
                // last until the activity is destroyed. ponytail: a recreation (a keyboard plugged in, a
                // theme switch) during a long multi-file copy leaves the rest unreadable, which the
                // attachment rows then say; copy inside the drop if that turns up
                activity?.requestDragAndDropPermissions(drag)
                val media = mediaIn(drag.clipData ?: return false, context)
                if (media.isNotEmpty()) attach(media)
                return media.isNotEmpty()
            }
        }
    }
}
