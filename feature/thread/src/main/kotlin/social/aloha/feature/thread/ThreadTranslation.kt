// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import social.aloha.core.ui.LocalStatusTranslations

/**
 * Translate thread, for the thread's menu: one tap translates every post in a language the reader does
 * not read, and the replies that arrive while the thread is open, until Show originals. Never without
 * the tap; null when no post here needs it.
 */
@Composable
internal fun rememberThreadTranslation(items: List<ThreadItem>): Pair<Int, () -> Unit>? {
    val translations = LocalStatusTranslations.current
    var on by rememberSaveable { mutableStateOf(false) }
    // a post shown in its original again stays so, also once the thread is back on screen: only posts
    // not asked about yet are translated
    val asked = rememberSaveable(saver = ASKED) { HashSet<String>() }
    val rows = remember(items) { items.mapNotNull { (it as? ThreadItem.Post)?.row } }
    val ids = remember(rows) { rows.map { it.statusId } }
    val foreign by remember(rows, translations) { derivedStateOf { rows.any(translations::offers) } }
    LaunchedEffect(on, ids) {
        if (!on) return@LaunchedEffect
        rows.filter { it.statusId !in asked && translations.stateOf(it.statusId) == null && translations.offers(it) }
            .forEach { row ->
                asked += row.statusId
                translations.translate(row)
            }
    }
    return when {
        on -> R.string.thread_show_originals to {
            on = false
            asked.clear()
            rows.forEach { translations.showOriginal(it.statusId) }
        }

        foreign -> R.string.thread_translate to { on = true }

        else -> null
    }
}

private val ASKED = listSaver<HashSet<String>, String>({ it.toList() }, { HashSet(it) })
