// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.model.GifEntry
import social.aloha.core.model.SignedInAccount

/** The GIF library as the sheet shows it: what was searched for and what came back so far. */
@Immutable
internal data class GifsUi(
    val query: String = "",
    val gifs: List<GifEntry> = emptyList(),
    val loading: Boolean = false,
    val end: Boolean = false,
    val attribution: String? = null,
    val failed: Boolean = false,
)

/**
 * What the server already has, attached without anything being uploaded: a GIF from its own library,
 * or a file from the writer's Nextcloud by path. Nothing about a search leaves the server.
 */
internal class Library(
    private val compose: ComposeRepository,
    private val attachments: Attachments,
    private val scope: CoroutineScope,
    /** The post being written, while it has room for another attachment. */
    private val target: () -> Int?,
) {
    private val gifState = MutableStateFlow(GifsUi())
    val gifs: StateFlow<GifsUi> = gifState.asStateFlow()

    private var search: Job? = null

    /** Searches for [query], a quarter second after typing stops; empty lists the library's own order. */
    fun onQuery(query: String) {
        gifState.value = GifsUi(query = query, loading = true)
        search?.cancel()
        search = scope.launch {
            delay(DEBOUNCE_MILLIS)
            load(query, offset = 0)
        }
    }

    /** The next page, once the list nears its end. */
    fun onMore() {
        val state = gifState.value
        if (state.loading || state.end) return
        gifState.update { it.copy(loading = true) }
        search = scope.launch { load(state.query, state.gifs.size) }
    }

    /** Attaches [gif] to the post being written; the server copies it, nothing is uploaded. */
    fun onGif(gif: GifEntry) = attach { account, segment ->
        val answer = compose.attachGif(account, gif.slug, gif.title)
        // the title went out as the description, whether or not the server says so back
        if (answer is Answer.Got) {
            attachments.put(
                segment,
                remoteAttachment(answer.value.copy(description = answer.value.description ?: gif.title), gif.title),
            )
        }
        answer is Answer.Got
    }

    /** Attaches the file at [path] in the writer's Nextcloud to the post being written. */
    fun onNextcloudFile(path: String) = attach { account, segment ->
        val clean = path.trim().removePrefix("/")
        val answer = compose.attachFile(account, clean)
        if (answer is Answer.Got) {
            attachments.put(
                segment,
                remoteAttachment(answer.value, clean.substringAfterLast('/')),
            )
        }
        answer is Answer.Got
    }

    private fun attach(made: suspend (SignedInAccount, segment: Int) -> Boolean) {
        val account = attachments.account ?: return
        val segment = target() ?: return
        scope.launch { if (!made(account, segment)) attachments.failure.value = AttachFailure.NotFound }
    }

    /** The paths attached before, newest first, offered under the path field. */
    suspend fun recentPaths(): List<String> = attachments.account?.let { compose.recentPaths(it) }.orEmpty()

    private suspend fun load(query: String, offset: Int) {
        val account = attachments.account ?: return
        when (val answer = compose.gifs(account, query.takeIf { it.isNotBlank() }, offset)) {
            is Answer.Got -> gifState.update { state ->
                val all = (state.gifs.takeIf { offset > 0 }.orEmpty() + answer.value.gifs).distinctBy { it.slug }
                state.copy(
                    gifs = all,
                    loading = false,
                    end = answer.value.gifs.isEmpty() || all.size >= answer.value.total,
                    attribution = answer.value.attribution,
                    failed = false,
                )
            }

            is Answer.Missed -> gifState.update { it.copy(loading = false, failed = true) }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}
