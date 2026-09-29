// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.probe

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// virtual time is how a race between slow and fast candidates is tested without real waits
@OptIn(ExperimentalCoroutinesApi::class)
class RankRaceTest {
    private val candidates = (1..3).map {
        ProbeCandidate(it, "https://x.test/$it/".toHttpUrl(), CandidateKind.DomainRoot)
    }

    @Test
    fun `a fast worse candidate waits for a slow better one`() = runTest {
        val winner = lowestRankFirst(candidates, 10.seconds) { candidate ->
            delay(if (candidate.rank == 1) 3_000 else 10)
            "answer ${candidate.rank}"
        }
        assertEquals(1, winner?.first?.rank)
    }

    @Test
    fun `the best success is taken once every better candidate failed, without waiting for worse ones`() = runTest {
        val winner = lowestRankFirst(candidates, 10.seconds) { candidate ->
            when (candidate.rank) {
                1 -> null.also { delay(100) }
                2 -> "two".also { delay(200) }
                else -> "three".also { delay(9_000) }
            }
        }
        assertEquals(2, winner?.first?.rank)
        assertEquals(200L, currentTime)
    }

    @Test
    fun `nothing answering within the budget is no winner`() = runTest {
        val winner = lowestRankFirst(candidates, 10.seconds) {
            delay(20_000)
            "late"
        }
        assertNull(winner)
        assertEquals(10_000L, currentTime)
    }

    @Test
    fun `every candidate failing is no winner`() = runTest {
        assertNull(lowestRankFirst(candidates, 10.seconds) { null })
    }

    @Test
    fun `the reprobe gate opens at most once an hour per host`() {
        var now = 0L
        val gate = ReprobeGate(nowMillis = { now })
        assertTrue(gate.tryAcquire("cloud.example"))
        assertEquals(false, gate.tryAcquire("cloud.example"))
        assertTrue(gate.tryAcquire("other.example"))
        now += 3_600_000L
        assertTrue(gate.tryAcquire("cloud.example"))
    }
}
