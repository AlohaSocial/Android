// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey

/** Where reading an account's posts from its own server stands, as Nextcloud Social reports it. */
public enum class ProfileSync { Pending, Failed }

private const val PROFILE_SYNC_READS = 10

/**
 * An account's posts its server is still reading from the account's own ([ProfileSync.Pending]): [key] read
 * afresh every [RefreshPlan.FILLING_DELAY], at most [PROFILE_SYNC_READS] times, each read's state emitted,
 * until the read is over or fails. A fresh read replaces the rows, so the caller stops collecting once the
 * reader pages further down.
 */
public fun TimelineRepository.followProfileSync(account: SignedInAccount, key: TimelineKey): Flow<ProfileSync?> = flow {
    repeat(PROFILE_SYNC_READS) {
        delay(RefreshPlan.FILLING_DELAY)
        when (val outcome = refresh(account, key, RefreshPlan.of(hasFetchedBefore = false, newestRowId = null))) {
            is PageOutcome.Loaded -> {
                emit(outcome.profileSync)
                if (outcome.profileSync != ProfileSync.Pending) return@flow
            }

            is PageOutcome.Failed -> return@flow

            PageOutcome.Busy -> Unit
        }
    }
}
