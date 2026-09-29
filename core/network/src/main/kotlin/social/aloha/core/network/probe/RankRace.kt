// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.probe

import kotlin.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs [attempt] for every candidate concurrently and returns the lowest-ranked success. A success
 * is accepted only once every better-ranked candidate has failed, so a fast answer never beats a
 * better one that is still on its way; once the winner is known the others are cancelled. Returns
 * null when nothing succeeded within [budget].
 */
internal suspend fun <T : Any> lowestRankFirst(
    candidates: List<ProbeCandidate>,
    budget: Duration,
    attempt: suspend (ProbeCandidate) -> T?,
): Pair<ProbeCandidate, T>? = withTimeoutOrNull(budget) {
    coroutineScope {
        val answers = Channel<Pair<ProbeCandidate, T?>>(Channel.UNLIMITED)
        val jobs = candidates.map { candidate -> launch { answers.send(candidate to attempt(candidate)) } }
        val pending = candidates.map { it.rank }.toSortedSet()
        var best: Pair<ProbeCandidate, T>? = null
        while (pending.isNotEmpty()) {
            val (candidate, value) = answers.receive()
            pending.remove(candidate.rank)
            if (value != null && (best == null || candidate.rank < best.first.rank)) best = candidate to value
            val leader = best
            if (leader != null && pending.none { it < leader.first.rank }) break
        }
        jobs.forEach { it.cancel() }
        best
    }
}
