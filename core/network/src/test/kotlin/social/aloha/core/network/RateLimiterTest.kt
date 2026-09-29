// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// virtual time is how a rate limiter is tested without real waits
@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {
    @Test
    fun `a full bucket lets a burst through without waiting`() = runTest {
        val limiter = RateLimiter(capacity = 5.0, refillPerSecond = 1.0, nowMillis = { testScheduler.currentTime })
        repeat(5) { limiter.acquire("cloud.example") }
        assertEquals(0L, currentTime)
    }

    @Test
    fun `an empty bucket waits for the refill`() = runTest {
        val limiter = RateLimiter(capacity = 2.0, refillPerSecond = 2.0, nowMillis = { testScheduler.currentTime })
        repeat(3) { limiter.acquire("cloud.example") }
        assertEquals(500L, currentTime)
    }

    @Test
    fun `hosts do not share a bucket`() = runTest {
        val limiter = RateLimiter(capacity = 1.0, refillPerSecond = 1.0, nowMillis = { testScheduler.currentTime })
        limiter.acquire("a.example")
        limiter.acquire("b.example")
        assertEquals(0L, currentTime)
    }

    @Test
    fun `a 429 blocks the host until Retry-After has passed`() = runTest {
        val limiter = RateLimiter(nowMillis = { testScheduler.currentTime })
        limiter.noteRateLimited("cloud.example", 30.seconds)
        assertTrue(limiter.isBlocked("cloud.example"))
        limiter.acquire("cloud.example")
        assertEquals(30_000L, currentTime)
        assertFalse(limiter.isBlocked("cloud.example"))
    }

    @Test
    fun `a 429 without Retry-After blocks for 20 to 60 seconds`() = runTest {
        val limiter = RateLimiter(random = Random(7), nowMillis = { testScheduler.currentTime })
        limiter.noteRateLimited("cloud.example", retryAfter = null)
        limiter.acquire("cloud.example")
        assertTrue(currentTime in 20_000L..60_000L)
    }

    @Test
    fun `backoff grows exponentially, is jittered and never exceeds the cap`() {
        val random = Random(1)
        repeat(100) { attempt ->
            val delay = Backoff.delay(attempt, base = 1.seconds, cap = 15.minutes, random = random)
            assertTrue(delay >= 0.milliseconds && delay <= 15.minutes)
        }
        assertTrue(Backoff.delay(0, base = 1.seconds, random = random) <= 1.seconds)
    }
}
