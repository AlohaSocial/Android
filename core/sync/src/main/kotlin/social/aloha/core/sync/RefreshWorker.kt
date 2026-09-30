// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.model.PollFrequency

/**
 * The periodic refresh while the app is closed. It has a hard budget, and whatever it did not reach in
 * time waits for the next run; it never fetches timelines.
 */
@HiltWorker
internal class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        withTimeoutOrNull(BUDGET) { engine.refreshInBackground() }
        return Result.success()
    }

    private companion object {
        val BUDGET = 25.seconds
    }
}

/**
 * Keeps the periodic refresh scheduled at the pace of the most frequent account, and cancelled when every
 * account refreshes only by hand or none is signed in. WorkManager allows no less than 15 minutes.
 */
public class BackgroundRefresh @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val settings: SyncSettings,
) {
    /** Runs until cancelled, rescheduling whenever an account or its pace changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    public suspend fun keepScheduled() {
        accounts.accounts.flatMapLatest { all ->
            if (all.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(all.map { settings.pollFrequency(it.id) }) { it.toList() }
            }
        }.distinctUntilChanged().collect { frequencies -> schedule(repeatFor(frequencies)) }
    }

    private fun schedule(repeat: Duration?) {
        val work = WorkManager.getInstance(context)
        if (repeat == null) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(repeat.toJavaDuration())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    internal companion object {
        const val NAME = "refresh"
        private val FLOOR = 15.minutes

        /** The fastest non-manual pace wins; null when there is none. */
        fun repeatFor(frequencies: List<PollFrequency>): Duration? =
            frequencies.mapNotNull { it.multiplier }.minOrNull()?.let { maxOf(FLOOR, FLOOR * it) }
    }
}
