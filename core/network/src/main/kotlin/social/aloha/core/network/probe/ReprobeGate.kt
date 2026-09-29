// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.probe

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Decides when an account's API base that stopped answering is probed for anew: an administrator
 * may have added or removed the rewrite rules. At most once per [interval] per host,
 * so a genuinely missing route does not probe on every request.
 */
public class ReprobeGate(private val nowMillis: () -> Long, private val interval: Duration = 1.hours) {
    private val lastProbe = ConcurrentHashMap<String, Long>()

    /** True, and remembered, when [host] may be probed again now. */
    public fun tryAcquire(host: String): Boolean {
        val now = nowMillis()
        var granted = false
        lastProbe.compute(host) { _, last ->
            if (last == null || now - last >= interval.inWholeMilliseconds) {
                granted = true
                now
            } else {
                last
            }
        }
        return granted
    }
}
