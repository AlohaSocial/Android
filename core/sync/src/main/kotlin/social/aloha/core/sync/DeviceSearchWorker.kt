// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import social.aloha.core.data.AccountRepository

/** Refreshes the phone's search with what each signed-in account has, but one that needs a new sign-in. */
@HiltWorker
internal class DeviceSearchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val search: DeviceSearch,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        accounts.all().filterNot { it.needsReauth }.forEach { search.refresh(it) }
        return Result.success()
    }
}
