// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import java.util.UUID
import social.aloha.core.sync.ExportRun

/**
 * Follows the exports a page asked for, among all of the account's: [ended] hears each once it is saved
 * or has failed. Exports finished before the page opened are not its news and pass unheard.
 */
internal class ExportFollower(private val ended: (ExportRun) -> Unit) {
    private val asked = mutableSetOf<UUID>()
    private var runs = emptyList<ExportRun>()

    fun seen(latest: List<ExportRun>) {
        runs = latest
        latest.filter { it.outcome != ExportRun.Outcome.Running && asked.remove(it.id) }.forEach(ended)
    }

    /** An export the page has just asked for, which may have ended already. */
    fun asked(id: UUID) {
        asked += id
        seen(runs)
    }
}
