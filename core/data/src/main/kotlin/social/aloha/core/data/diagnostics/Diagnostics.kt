// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.network.di.IoDispatcher

/** This build: its version name and its distribution flavour. */
public data class AppBuild(val version: String, val flavour: String)

/**
 * The plain-text report Settings, About shares when the person asks: the build, the device, each
 * account's server software and version (no handle, no host), the last process exits and the
 * [LogBuffer]. The person reads it before it goes anywhere; the app never sends it.
 */
public class Diagnostics @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val build: AppBuild,
    private val accounts: AccountRepository,
    private val log: LogBuffer,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    public suspend fun report(): String = withContext(ioDispatcher) {
        val head = buildString {
            appendLine("Aloha Social ${build.version} (${build.flavour})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("Servers")
            accounts.all().forEach {
                appendLine("- ${it.capabilities.softwareName.ifEmpty { "unknown" }} ${it.capabilities.softwareVersion}")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                appendLine()
                appendLine("Process exits")
                exits().forEach { appendLine(it) }
            }
            appendLine()
            appendLine("Log")
        }
        head + lastLines(MAX_CHARS - head.length).joinToString("") { it + "\n" }
    }

    /** The newest log lines that fit in [budget] characters, oldest first. */
    private fun lastLines(budget: Int): List<String> {
        var used = 0
        return log.lines().asReversed().takeWhile { line ->
            used += line.length + 1
            used <= budget
        }.asReversed()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exits(): List<String> = context.getSystemService<ActivityManager>()
        ?.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
        .orEmpty()
        .map { exit ->
            val reason = reasons()[exit.reason] ?: "reason ${exit.reason}"
            val description = LogBuffer.redact(exit.description.orEmpty())
            val line = "- ${Instant.ofEpochMilli(exit.timestamp)} $reason: $description"
            if (exit.reason == ApplicationExitInfo.REASON_ANR) line + "\n" + trace(exit) else line
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reasons(): Map<Int, String> = mapOf(
        ApplicationExitInfo.REASON_EXIT_SELF to "exited",
        ApplicationExitInfo.REASON_SIGNALED to "signalled",
        ApplicationExitInfo.REASON_LOW_MEMORY to "low memory",
        ApplicationExitInfo.REASON_CRASH to "crash",
        ApplicationExitInfo.REASON_CRASH_NATIVE to "native crash",
        ApplicationExitInfo.REASON_ANR to "not responding",
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to "failed to start",
        ApplicationExitInfo.REASON_PERMISSION_CHANGE to "permission changed",
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to "excessive resource use",
        ApplicationExitInfo.REASON_USER_REQUESTED to "stopped by the user",
        ApplicationExitInfo.REASON_USER_STOPPED to "force-stopped",
        ApplicationExitInfo.REASON_DEPENDENCY_DIED to "a dependency died",
        ApplicationExitInfo.REASON_OTHER to "other",
    )

    // ponytail: only the head of an ANR trace, the main thread; all of it if that ever falls short
    @RequiresApi(Build.VERSION_CODES.R)
    private fun trace(exit: ApplicationExitInfo): String = exit.traceInputStream?.bufferedReader()?.use { reader ->
        reader.lineSequence().take(MAX_TRACE_LINES).joinToString("\n") { "    $it" }
    }.orEmpty()

    private companion object {
        /** Kept well under what an intent can carry through the share sheet. */
        const val MAX_CHARS = 200_000
        const val MAX_EXITS = 5
        const val MAX_TRACE_LINES = 60
    }
}
