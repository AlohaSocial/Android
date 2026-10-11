// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import java.time.Instant

/**
 * A file or folder in a person's Nextcloud Files, as a listing describes it.
 *
 * @property path from the root of the person's files, without a leading slash: what Social's
 *   `media/from-file` takes, and what the next listing is asked for.
 * @property permissions Nextcloud's letters: `G` readable, `C` new files may be made in a folder,
 *   `R` re-shareable, `W` writable, `D` deletable.
 * @property hasPreview the server can draw a preview of it.
 * @property encrypted end-to-end encrypted: the server cannot read it, so it cannot be attached.
 */
public data class NextcloudFile(
    val path: String,
    val isFolder: Boolean,
    val mimeType: String?,
    val size: Long,
    val modified: Instant?,
    val etag: String,
    val fileId: Long?,
    val permissions: String,
    val hasPreview: Boolean,
    val encrypted: Boolean,
) {
    val name: String get() = path.substringAfterLast('/')

    /** Its contents may be read, by the person and so by Social copying it into a post. */
    val readable: Boolean get() = 'G' in permissions && !encrypted

    /** A folder new files may be made in. */
    val canAddFiles: Boolean get() = isFolder && 'C' in permissions
}

/**
 * A folder as a listing describes it: the [folder] itself, which says what may be done in it, null when
 * the server left it out, and the [files] and folders in it.
 */
public data class NextcloudFolder(val folder: NextcloudFile?, val files: List<NextcloudFile>)
