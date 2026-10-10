// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.di.IoDispatcher

/**
 * Writes one export into the document the reader picked, as foreground work that the notification can
 * cancel. Nothing is retried: a failed or cancelled export has what it wrote deleted, and a second
 * attempt would have no document to write into. The notification at the end says how it went.
 */
@HiltWorker
internal class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val extras: NextcloudExtras,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : CoroutineWorker(context, params) {
    private val notifications = ExportNotifications(context)

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.running(id)

    override suspend fun doWork(): Result {
        val account = inputData.getString(ACCOUNT)?.let { accounts.byId(it) }
        val job = inputData.getString(JOB)?.let(::exportJobOf)
        val uri = inputData.getString(URI)?.toUri()
        if (account == null || job == null || uri == null) {
            // nothing was written yet, and the document may be one the reader already had: it stays
            uri?.let { release(it) }
            notifications.finished(id, saved = false)
            return Result.failure()
        }
        tryForeground()
        var saved = false
        try {
            saved = save(uri) { write(account, job, it) }
        } finally {
            withContext(NonCancellable) {
                if (!saved) delete(uri)
                release(uri)
                // cancelled from the notification: the reader knows, there is nothing to tell
                if (!isStopped) notifications.finished(id, saved)
            }
        }
        return if (saved) Result.success() else Result.failure()
    }

    private suspend fun write(account: SignedInAccount, job: ExportJob, out: OutputStream): Answer<Unit> = when (job) {
        ExportJob.Archive -> extras.exportAccount(account, out)
        is ExportJob.ListOf -> extras.exportList(account, job.kind, out)
        is ExportJob.Statistics -> extras.exportStatistics(account, job.days, out)
    }

    /** True once all of what [write] produces is in the document at [uri]. */
    private suspend fun save(uri: Uri, write: suspend (OutputStream) -> Answer<Unit>): Boolean = try {
        // "wt": a document picked over an existing one is truncated, not overwritten from the start
        withContext(io) {
            applicationContext.contentResolver.openOutputStream(uri, "wt")?.use { write(it) }
        } is Answer.Got
    } catch (_: IOException) {
        false // the provider would not open the document, or failed while it was written
    } catch (_: SecurityException) {
        false // the grant to the document was gone by the time it was written
    } catch (_: IllegalArgumentException) {
        false // the provider no longer knows the document
    }

    /** Deletes what a failed export left, where the document's provider lets it. */
    private suspend fun delete(uri: Uri) = withContext(io) {
        runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, uri) }
    }

    private fun release(uri: Uri) = runCatching {
        applicationContext.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    companion object {
        const val ACCOUNT = "account"
        const val JOB = "job"
        const val URI = "uri"
    }
}
