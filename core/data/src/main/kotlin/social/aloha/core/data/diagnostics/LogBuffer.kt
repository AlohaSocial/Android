// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.diagnostics

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * The release build's log: the last [CAPACITY] lines at `INFO` and above, each redacted and then cut
 * at [LINE_LIMIT] characters. It lives in memory only; nothing writes it to disk or sends it, and it
 * leaves the app only in the diagnostics report the person shares themselves.
 */
@Singleton
public class LogBuffer @Inject constructor(private val clock: Clock) : Timber.Tree() {
    private val lines = ArrayDeque<String>()

    public fun lines(): List<String> = synchronized(lines) { lines.toList() }

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val time = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
        val line = "$time ${LEVELS.getOrElse(priority - VERBOSE) { '?' }}/${tag ?: "-"}: ${redact(message)}"
        synchronized(lines) {
            if (lines.size == CAPACITY) lines.removeFirst()
            lines.addLast(line.take(LINE_LIMIT))
        }
    }

    public companion object {
        public const val CAPACITY: Int = 500
        public const val LINE_LIMIT: Int = 1_000

        private const val VERBOSE = 2
        private const val INFO = 4
        private const val LEVELS = "VDIWEA"
        private const val REDACTED = "[redacted]"
        private const val UUID = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""

        private val query = Regex("""((?:[A-Za-z][A-Za-z0-9+.-]*://|/)[^\s?#"'<>]*)\?[^\s#"'<>]*""")
        private val keyed = Regex(
            """(?i)\b(authorization|code|code_verifier|[a-z_]*(?:token|secret|password))("?\s*[:=]\s*)""" +
                """(?:(?:bearer|basic)\s+)?"?[^\s"&,;}]+""",
        )
        private val bearer = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
        private val tokenLike = Regex(
            """(?<![A-Za-z0-9_-])(?!$UUID(?![A-Za-z0-9_-]))""" +
                """(?=[A-Za-z_-]*[0-9])(?=[0-9_-]*[A-Za-z])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9_-])""",
        )

        /**
         * Removes what must never be kept: the query of any address, the value of a credential, whatever
         * follows `Bearer`, and any run of 32 or more token characters mixing letters and digits.
         */
        public fun redact(message: String): String = message
            .replace(query, "$1?$REDACTED")
            .replace(keyed, "$1$2$REDACTED")
            .replace(bearer, "Bearer $REDACTED")
            .replace(tokenLike, REDACTED)
    }
}
