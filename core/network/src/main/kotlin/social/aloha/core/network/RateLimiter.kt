// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A token bucket per host, shared by every account on that host, so five accounts on one small
 * instance behave like one. A 429 blocks the host until `Retry-After`, or 20–60 s when the server
 * sent none.
 *
 * @param nowMillis the clock in milliseconds, injected so tests run on virtual time.
 */
public class RateLimiter(
    private val nowMillis: () -> Long,
    private val capacity: Double = DEFAULT_CAPACITY,
    private val refillPerSecond: Double = DEFAULT_REFILL_PER_SECOND,
    private val random: Random = Random.Default,
) {
    private class Bucket(var tokens: Double, var lastRefillMillis: Long, var blockedUntilMillis: Long = 0)

    private val mutex = Mutex()
    private val buckets = mutableMapOf<String, Bucket>()

    /** Suspends until [host] accepts another request. */
    public suspend fun acquire(host: String) {
        while (true) {
            val wait = mutex.withLock { reserve(host) }
            if (wait <= Duration.ZERO) return
            delay(minOf(wait, MAXIMUM_SINGLE_WAIT))
        }
    }

    private fun reserve(host: String): Duration {
        val now = nowMillis()
        val bucket = buckets.getOrPut(host) { Bucket(capacity, now) }
        if (bucket.blockedUntilMillis > now) return (bucket.blockedUntilMillis - now).milliseconds
        val elapsedSeconds = (now - bucket.lastRefillMillis) / MILLIS_PER_SECOND
        bucket.tokens = min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond)
        bucket.lastRefillMillis = now
        val available = bucket.tokens >= 1
        if (available) bucket.tokens -= 1
        val refillMillis = ((1 - bucket.tokens) / refillPerSecond * MILLIS_PER_SECOND).toLong()
        return if (available) Duration.ZERO else refillMillis.milliseconds
    }

    /** Records a 429 from [host]. */
    public suspend fun noteRateLimited(host: String, retryAfter: Duration?) {
        val delay =
            retryAfter
                ?: random.nextLong(FALLBACK_MIN.inWholeMilliseconds, FALLBACK_MAX.inWholeMilliseconds).milliseconds
        mutex.withLock {
            val now = nowMillis()
            val bucket = buckets.getOrPut(host) { Bucket(0.0, now) }
            bucket.tokens = 0.0
            bucket.lastRefillMillis = now
            bucket.blockedUntilMillis = now + delay.inWholeMilliseconds
        }
    }

    public suspend fun isBlocked(host: String): Boolean =
        mutex.withLock { (buckets[host]?.blockedUntilMillis ?: 0) > nowMillis() }

    private companion object {
        const val DEFAULT_CAPACITY = 30.0
        const val DEFAULT_REFILL_PER_SECOND = 3.0
        const val MILLIS_PER_SECOND = 1000.0
        val MAXIMUM_SINGLE_WAIT = 1.minutes
        val FALLBACK_MIN = 20.seconds
        val FALLBACK_MAX = 60.seconds
    }
}

/** Exponential backoff with full jitter, capped: retries and the poll scheduler's failure path. */
public object Backoff {
    public fun delay(
        attempt: Int,
        base: Duration = 1.seconds,
        cap: Duration = 15.minutes,
        random: Random = Random.Default,
    ): Duration {
        val exponent = attempt.coerceIn(0, MAXIMUM_EXPONENT)
        val ceiling = minOf(cap, base * (1L shl exponent).toDouble())
        return random.nextLong(0, ceiling.inWholeMilliseconds + 1).milliseconds
    }

    private const val MAXIMUM_EXPONENT = 30
}
