// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import social.aloha.core.model.MigrationListKind

/** What an export takes away from Nextcloud Social. */
public sealed interface ExportJob {
    /** The whole account: posts, media and follows. */
    public data object Archive : ExportJob

    /** One of the account's lists, as a CSV other servers import. */
    public data class ListOf(val kind: MigrationListKind) : ExportJob

    /** The account's statistics over [days], 0 for everything, as a CSV. */
    public data class Statistics(val days: Int) : ExportJob
}

internal fun ExportJob.wire(): String = when (this) {
    ExportJob.Archive -> "archive"
    is ExportJob.ListOf -> "list:${kind.wire}"
    is ExportJob.Statistics -> "statistics:$days"
}

internal fun exportJobOf(wire: String): ExportJob? = when {
    wire == "archive" -> ExportJob.Archive

    wire.startsWith("list:") ->
        MigrationListKind.entries.firstOrNull { it.wire == wire.removePrefix("list:") }?.let(ExportJob::ListOf)

    wire.startsWith("statistics:") -> wire.removePrefix("statistics:").toIntOrNull()?.let(ExportJob::Statistics)

    else -> null
}

/** One export of an account: waiting or going, saved, or not. */
public data class ExportRun(val id: UUID, val job: ExportJob, val outcome: Outcome) {
    public enum class Outcome { Running, Saved, Failed }
}

/**
 * Exports that outlive the page they were asked on: each is a WorkManager job, in the foreground with
 * a notification that can cancel it, and a notification once it is saved or has failed. The server
 * builds an archive before it sends the first byte, which can take minutes. The page only enqueues
 * and watches.
 *
 * The document is the one the reader picked with the system's save dialog; the app keeps its grant
 * until the export is done, so the export finishes even when the app is gone meanwhile.
 */
@Singleton
public class Exports @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val work get() = WorkManager.getInstance(context)

    /** Writes [job] of account [accountId] into the document at [uri]; returns the run to watch for. */
    public suspend fun enqueue(accountId: String, job: ExportJob, uri: Uri): UUID {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            // a provider that grants no lasting access: the export still runs while the grant lasts
        }
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(
                workDataOf(
                    ExportWorker.ACCOUNT to accountId,
                    ExportWorker.JOB to job.wire(),
                    ExportWorker.URI to uri.toString(),
                ),
            )
            .setConstraints(NEEDS_NETWORK)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .addTag(accountTag(accountId))
            .addTag(JOB_TAG + job.wire())
            .build()
        // one export per document: asked again while it is written, it is the one already going, whose
        // cleanup and grant stay its own; exports into different documents run side by side
        val name = "$TAG:$uri"
        work.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).await()
        return work.getWorkInfosForUniqueWorkFlow(name).first().firstOrNull { !it.state.isFinished }?.id ?: request.id
    }

    /** The exports of account [accountId] WorkManager still knows of, about a day's worth. */
    public fun observe(accountId: String): Flow<List<ExportRun>> =
        work.getWorkInfosByTagFlow(accountTag(accountId)).map { infos -> infos.mapNotNull { it.run() } }

    private fun WorkInfo.run(): ExportRun? {
        val job = tags.firstNotNullOfOrNull { it.removePrefix(JOB_TAG).takeIf { _ -> it.startsWith(JOB_TAG) } }
            ?.let(::exportJobOf)
            ?: return null
        val outcome = when (state) {
            WorkInfo.State.SUCCEEDED -> ExportRun.Outcome.Saved
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> ExportRun.Outcome.Failed
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> ExportRun.Outcome.Running
        }
        return ExportRun(id, job, outcome)
    }

    private fun accountTag(accountId: String) = "$TAG:$accountId"

    internal companion object {
        const val TAG = "export"
        const val JOB_TAG = "export-job:"
    }
}
