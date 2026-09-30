// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import social.aloha.core.ui.isForeignContent

/** What another app shared: text (a link, most often) and pictures or videos, for a new post. */
data class SharedContent(val text: String?, val media: List<Uri>) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && media.isEmpty()

    companion object {
        /** What [intent] shares, when it is a share; only other apps' content, as [isForeignContent] says. */
        fun from(intent: Intent, ownPackage: String): SharedContent? {
            if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
            val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            // a page shared from a browser comes as its title and its address; both go, once
            val text = listOfNotNull(subject?.takeIf { body == null || it !in body }, body)
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
                .ifEmpty { null }
            val media = streams(intent).filter { isForeignContent(it, ownPackage) }
            return SharedContent(text, media.distinct()).takeUnless { it.isEmpty }
        }

        private fun streams(intent: Intent): List<Uri> {
            val extra = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            }
            val clip = intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
            return extra + clip.orEmpty()
        }
    }
}
