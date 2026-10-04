// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Instant
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.model.Status

/**
 * Whether a status is shown, collapsed behind a warning, or hidden, from the v2 filters that apply in a
 * [FilterContext]. Evaluated when a row is drawn, not when it is stored, so a filter that expires stops
 * hiding at once without a refetch.
 */
public class FilterEvaluator(filters: List<Filter>, private val context: FilterContext, private val now: Instant) {
    public sealed interface Decision {
        public data object Show : Decision

        /** Shown behind a warning naming the filters' [titles]; [keywords] are the words that matched. */
        public data class Warn(val titles: List<String>, val keywords: List<String> = emptyList()) : Decision

        public data object Hide : Decision
    }

    // compiled once for every row drawn, not once per row
    private val active = filters.filter { context in it.context && !it.isExpired(now) }
        .map { filter ->
            filter to
                filter.keywords.filter { it.keyword.isNotBlank() }.map { pattern(it.keyword, it.wholeWord) }
        }

    /**
     * @param text the displayed status's plain text and content warning: keywords match what a person
     *   reads, not the markup (a keyword such as `class` must not match every link).
     */
    public fun decision(status: Status, text: String): Decision {
        val shown = status.displayed
        val serverResults = shown.filtered.orEmpty().filter {
            context in it.filter.context && !it.filter.isExpired(now)
        }
        val localMatches = active.filter { (filter, patterns) ->
            filter.statuses.any { it.statusId == shown.id } || patterns.any { it.containsMatchIn(text) }
        }
        val matching = (serverResults.map { it.filter } + localMatches.map { it.first }).distinctBy { it.id }
        return when {
            matching.isEmpty() -> Decision.Show

            // hiding wins over warning, whatever order the filters come in
            matching.any { it.filterAction == FilterAction.Hide } -> Decision.Hide

            else -> {
                val local = localMatches.flatMap { (_, patterns) ->
                    patterns.flatMap { it.findAll(text).map { m -> m.value } }
                }
                val keywords = serverResults.flatMap { it.keywordMatches.orEmpty() } + local
                Decision.Warn(matching.map { it.title }, keywords.distinctBy { it.lowercase() })
            }
        }
    }

    // A whole-word keyword may be a phrase ("new york"): it must not touch a letter or digit on either side.
    private fun pattern(keyword: String, wholeWord: Boolean): Regex {
        val literal = Regex.escape(keyword.trim())
        return Regex(
            if (wholeWord) "(?<![\\p{L}\\p{N}])$literal(?![\\p{L}\\p{N}])" else literal,
            RegexOption.IGNORE_CASE,
        )
    }
}
