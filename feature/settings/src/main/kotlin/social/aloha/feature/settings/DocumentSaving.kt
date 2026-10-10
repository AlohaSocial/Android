// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import social.aloha.core.data.Answer

/**
 * Writes what [write] produces into the document at [uri], which the reader picked with the system's
 * save dialog; true once all of it is there. A refused, failed or abandoned write deletes the document
 * where its provider lets it, rather than leave half a file behind.
 */
internal suspend fun saveToDocument(
    context: Context,
    uri: Uri,
    write: suspend (OutputStream) -> Answer<Unit>,
): Boolean {
    val resolver = context.contentResolver
    var saved = false
    try {
        saved = withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "w")?.use { write(it) } } is Answer.Got
    } catch (_: IOException) {
        // the provider would not open the document, or failed while it was written
    } catch (_: SecurityException) {
        // the permission to the document was gone by the time it was written
    } catch (_: IllegalArgumentException) {
        // the provider no longer knows the document
    } finally {
        if (!saved) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
        }
    }
    return saved
}
