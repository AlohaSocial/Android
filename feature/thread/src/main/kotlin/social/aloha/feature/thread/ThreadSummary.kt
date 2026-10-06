// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.IntelligencePreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.intelligence.Drafted
import social.aloha.core.intelligence.Intelligence

/** A thread's summary: on its way, made, or not made. */
internal sealed interface Summary {
    data object Working : Summary

    /** [text], made from the first [read] of the thread's [of] posts. */
    data class Done(val text: String, val read: Int, val of: Int) : Summary

    data class Declined(val why: Drafted) : Summary
}

/**
 * A thread summarised on the device, from its posts as shown, in order, each with its author. It lives
 * in a sheet while open and nowhere else: never kept, never put in the thread.
 */
@HiltViewModel
internal class SummaryViewModel @Inject constructor(
    private val intelligence: Intelligence,
    preferences: IntelligencePreferences,
) : ViewModel() {
    private val shown = MutableStateFlow<Summary?>(null)
    private var job: Job? = null

    val summary: StateFlow<Summary?> = shown.asStateFlow()

    /** Only with Summarise on and the model on the device; until then the model is not asked anything. */
    val offered: StateFlow<Boolean> = intelligence.ready(preferences.choices.map { it.summary })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), false)

    fun summarise(items: List<ThreadItem>) {
        val passages = items.mapNotNull { (it as? ThreadItem.Post)?.row }
            // a post the reader's own filters fold away is left out, and only the warning of one behind it read:
            // the summary must not say what either hides
            .filter { it.filterWarning == null }
            .mapNotNull { row ->
                val text = row.spoiler?.text?.takeIf { it.isNotBlank() } ?: row.plainText.takeIf { it.isNotBlank() }
                text?.let { "${row.author.handle}: $it" }
            }
        if (passages.isEmpty()) return
        job?.cancel()
        shown.value = Summary.Working
        job = viewModelScope.launch {
            val drafted = intelligence.summarise(passages)
            shown.value = if (drafted is Drafted.Text) {
                Summary.Done(drafted.text, intelligence.summaryReach(passages), passages.size)
            } else {
                Summary.Declined(drafted)
            }
        }
    }

    fun dismiss() {
        job?.cancel()
        shown.value = null
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

/** The summary in a sheet, said to be generated; closing it is going back to the thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummarySheet(summary: Summary, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            Text(
                stringResource(R.string.thread_summary_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            val said = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            when (summary) {
                Summary.Working -> {
                    Text(stringResource(R.string.thread_summarising), modifier = said)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                is Summary.Done -> Column(said, verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                    Text(
                        stringResource(R.string.thread_summary_generated),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (summary.read < summary.of) {
                        Text(
                            pluralStringResource(R.plurals.thread_summary_part, summary.of, summary.read, summary.of),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(summary.text, style = MaterialTheme.typography.bodyLarge)
                }

                is Summary.Declined -> {
                    val why = if (summary.why ==
                        Drafted.Refused
                    ) {
                        R.string.thread_summary_refused
                    } else {
                        R.string.thread_summary_failed
                    }
                    Text(stringResource(why), style = MaterialTheme.typography.bodyLarge, modifier = said)
                }
            }
            val close = if (summary == Summary.Working) R.string.thread_summary_cancel else R.string.thread_summary_read
            TextButton(onClick = onDismiss) { Text(stringResource(close)) }
        }
    }
}

/** The thread's own menu, with only what is on; none at all when nothing is. */
@Composable
internal fun ThreadMenu(entries: List<Pair<Int, () -> Unit>>) {
    if (entries.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.thread_menu)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { (label, onClick) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        onClick()
                    },
                )
            }
        }
    }
}
