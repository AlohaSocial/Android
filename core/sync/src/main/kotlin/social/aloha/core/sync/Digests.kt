// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.data.sync.WidgetUpdates
import social.aloha.core.model.Digest
import social.aloha.core.model.QuietHours

/**
 * One digest time: raises what was held back, lets the widgets draw what they were given meanwhile, and
 * books the next time. Nothing on the device is lost when it is late or skipped: the next run finds
 * whatever is still unread.
 */
@HiltWorker
internal class DigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val raiser: NotificationRaiser,
    private val widgets: WidgetUpdates,
    private val digests: DigestScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        raiser.digest()
        widgets.redraw()
        digests.scheduleNext()
        return Result.success()
    }
}

/**
 * Keeps the next digest booked with WorkManager, which runs it within its window of the hour chosen and
 * needs no alarm permission; a time inside quiet hours waits for them to end. Booked again whenever the
 * digest or the quiet hours change, and cancelled when notifications come as they arrive.
 */
public class DigestScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SyncSettings,
    private val clock: Clock,
) {
    /** Runs until cancelled, booking the next digest whenever what decides it changes. */
    public suspend fun keepScheduled() {
        combine(settings.digest, settings.quietHours, ::Pair).distinctUntilChanged().collect { (digest, quiet) ->
            schedule(digest, quiet)
        }
    }

    /** Books the digest after the one that just ran. */
    internal suspend fun scheduleNext() {
        schedule(settings.digest.first(), settings.quietHours.first())
    }

    private fun schedule(digest: Digest?, quiet: QuietHours?) {
        val work = WorkManager.getInstance(context)
        if (digest == null) {
            work.cancelUniqueWork(NAME)
            return
        }
        val now = clock.instant().atZone(ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInitialDelay(Duration.between(now, digest.nextAfter(now, quiet)))
            .setConstraints(NEEDS_NETWORK)
            .build()
        work.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val NAME = "digest"
    }
}
