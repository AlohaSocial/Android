// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.content.ContentResolver
import android.net.Uri

/**
 * Whether [uri] is another app's content, one the app may read and post: a content address, and
 * not one of the app's own ([ownPackage] and below). A file path, or an address of the app's own,
 * would have it read, and post, its own files.
 */
public fun isForeignContent(uri: Uri, ownPackage: String): Boolean =
    uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority?.startsWith(ownPackage) != true
