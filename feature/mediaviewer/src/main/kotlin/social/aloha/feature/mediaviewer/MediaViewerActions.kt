// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.mediaviewer

import social.aloha.core.data.timeline.Toggle
import social.aloha.core.model.MediaAttachment

/** What the viewer can do with the attachment on screen, beyond looking at it. */
internal interface MediaViewerActions {
    fun onClose()
    fun onSave(attachment: MediaAttachment)
    fun onShare(attachment: MediaAttachment)
    fun onCopy(attachment: MediaAttachment)
    fun onOpenInBrowser(attachment: MediaAttachment)
    fun onReport()
    fun onReply()
    fun onToggle(toggle: Toggle)
}
