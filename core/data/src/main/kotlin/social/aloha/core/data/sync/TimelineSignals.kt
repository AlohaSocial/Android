// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Between the timeline on screen and the poll that keeps it fresh. The screen says when it shows an
 * account's timeline; a poll then says the timeline is due, and the timeline refreshes itself so what
 * arrives waits behind its pill. A timeline that is not on screen is left alone: it refreshes itself
 * when it comes back, if it is stale by then.
 */
@Singleton
public class TimelineSignals @Inject constructor() {
    private val due = MutableSharedFlow<String>(extraBufferCapacity = DUE_BUFFER)
    private val onScreen = ConcurrentHashMap.newKeySet<String>()

    /** The ids of accounts whose timeline on screen should refresh now. */
    public val timelineDue: SharedFlow<String> = due

    public fun noteShown(accountId: String, isShown: Boolean) {
        if (isShown) onScreen += accountId else onScreen -= accountId
    }

    public fun onScreen(accountId: String): Boolean = accountId in onScreen

    public fun markDue(accountId: String) {
        due.tryEmit(accountId)
    }

    private companion object {
        const val DUE_BUFFER = 8
    }
}
