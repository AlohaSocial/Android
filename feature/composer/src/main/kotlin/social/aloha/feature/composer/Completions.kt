// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.SignedInAccount

/**
 * What the word at the cursor could become: accounts for `@` and hashtags for `#` from the server,
 * each asked once a quarter second after typing stops and kept for the rest of the session, and the
 * server's custom emoji for `:`, filtered here. The hashtag as typed comes first, then the writer's
 * recent ones. Only emoji the server lists in its picker are offered; one typed by hand still posts and
 * renders.
 */
internal class Completions(private val compose: ComposeRepository, private val scope: CoroutineScope) {
    private val found = MutableStateFlow(CompletionsUi())
    val offered: StateFlow<CompletionsUi> = found.asStateFlow()

    private val asked = HashMap<Pair<CompletionKind, String>, List<Suggestion>>()
    private var job: Job? = null

    /** The writer is at [completing] (null: nowhere a completion applies) writing as [reader]. */
    fun onTyping(reader: SignedInAccount, completing: Completing?, emojis: List<CustomEmoji>) {
        job?.cancel()
        val cacheKey = completing?.let { it.kind to it.query.lowercase() }
        val known = when {
            completing == null -> emptyList()
            completing.kind == CompletionKind.Emoji -> emoji(completing.query, emojis)
            else -> asked[cacheKey]
        }
        if (completing == null) {
            found.value = CompletionsUi()
            return
        }
        val shown = CompletionsUi(completing.kind, completing.query, known.orEmpty(), loading = known == null)
        found.value = shown
        if (known != null) return
        job = scope.launch {
            delay(DEBOUNCE_MILLIS)
            val result = when (completing.kind) {
                CompletionKind.Account -> accounts(reader, completing.query)
                else -> hashtags(reader, completing.query)
            }
            if (result != null && cacheKey != null) asked[cacheKey] = result
            found.value = shown.copy(items = result ?: typed(completing), loading = false)
        }
    }

    /** The writer switched account: what one server knew says nothing about another's. */
    fun forget() {
        job?.cancel()
        asked.clear()
        found.value = CompletionsUi()
    }

    // what was typed is offered as it is, a new hashtag being as good as a known one
    private fun typed(completing: Completing): List<Suggestion> =
        if (completing.kind == CompletionKind.Hashtag) listOf(tag(completing.query)) else emptyList()

    private fun tag(name: String) = Suggestion("#$name", "#$name", null)

    private suspend fun accounts(reader: SignedInAccount, query: String): List<Suggestion>? =
        (compose.accounts(reader, query) as? Answer.Got)?.value?.map { account ->
            val name = account.displayName.ifBlank { account.username }
            Suggestion("@${account.acct}", "$name · @${account.acct}", account.avatar)
        }

    private suspend fun hashtags(reader: SignedInAccount, query: String): List<Suggestion>? {
        val recent = compose.recentTags(reader).filter { it.startsWith(query, ignoreCase = true) }
        val server = (compose.hashtags(reader, query) as? Answer.Got)?.value ?: return null
        return (listOf(query) + recent + server).distinctBy(String::lowercase).take(MAX_SHOWN).map(::tag)
    }

    private fun emoji(query: String, emojis: List<CustomEmoji>): List<Suggestion> {
        val listed = emojis.filter { it.visibleInPicker && it.shortcode.contains(query, ignoreCase = true) }
        // those starting with what was typed first, then those containing it
        val ordered = listed.sortedBy { !it.shortcode.startsWith(query, ignoreCase = true) }
        return ordered.take(MAX_SHOWN).map { Suggestion(":${it.shortcode}:", ":${it.shortcode}:", it.url) }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
        const val MAX_SHOWN = 8
    }
}
