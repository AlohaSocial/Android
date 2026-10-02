// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.safety.Blocking
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount

/** Someone blocked or muted, as a row shows them. */
@Immutable
internal data class Kept(val id: String, val name: String, val handle: String, val avatarUrl: String?)

internal enum class BlockedTab { Blocked, Muted, Servers }

/**
 * The three lists, each null until it arrived (or when it could not be asked: [failed]). [refused] after
 * the server would not take a change back, which then stands as it was.
 */
@Immutable
internal data class BlockedState(
    val blocked: List<Kept>? = null,
    val muted: List<Kept>? = null,
    val servers: List<String>? = null,
    val failed: Boolean = false,
    val refused: Boolean = false,
)

@HiltViewModel(assistedFactory = BlockedViewModel.Factory::class)
internal class BlockedViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val blocking: Blocking,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): BlockedViewModel
    }

    private val current = MutableStateFlow(BlockedState())
    val state: StateFlow<BlockedState> = current.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val reader = accounts.byId(readerId) ?: return@launch
            val blocked = async { blocking.blocked(reader) }
            val muted = async { blocking.muted(reader) }
            val servers = async { blocking.servers(reader) }
            val answers = listOf(blocked.await(), muted.await(), servers.await())
            current.value = BlockedState(
                blocked = (blocked.await() as? Answer.Got)?.value?.map(::kept),
                muted = (muted.await() as? Answer.Got)?.value?.map(::kept),
                servers = (servers.await() as? Answer.Got)?.value?.sorted(),
                failed = answers.any { it is Answer.Missed },
            )
        }
    }

    fun unblock(id: String) = takeBack(BLOCKED, { it.id == id }) { reader -> blocking.unblock(reader, id) }

    fun unmute(id: String) = takeBack(MUTED, { it.id == id }) { reader -> blocking.unmute(reader, id) }

    fun unblockServer(domain: String) =
        takeBack(SERVERS, { it == domain }) { reader -> blocking.unblockServer(reader, domain) }

    /** [domain] as typed, trimmed of a scheme, a path and an `@`; false when nothing is left of it. */
    fun blockServer(typed: String): Boolean {
        val domain = typed.trim().removePrefix("https://").removePrefix("http://").removePrefix("@")
            .substringBefore('/').substringAfterLast('@').lowercase()
        if (domain.isEmpty() || '.' !in domain) return false
        current.update { it.copy(servers = (it.servers.orEmpty() + domain).distinct().sorted(), refused = false) }
        viewModelScope.launch {
            val reader = accounts.byId(readerId)
            // refused, that server alone leaves the list again
            if (reader == null || !blocking.blockServer(reader, domain)) {
                current.update { it.copy(servers = it.servers?.minus(domain), refused = true) }
            }
        }
        return true
    }

    fun onRefusalShown() {
        current.update { it.copy(refused = false) }
    }

    /**
     * The row [matches] picks leaves [list] at once; where the server refused, it alone comes back where it
     * was, and that is said. Whatever else was taken back meanwhile stays taken back.
     */
    private fun <T> takeBack(list: Rows<T>, matches: (T) -> Boolean, send: suspend (SignedInAccount) -> Boolean) {
        val rows = list.get(current.value).orEmpty()
        val at = rows.indexOfFirst(matches)
        val row = rows.getOrNull(at) ?: return
        current.update { list.put(it, list.get(it)?.filterNot(matches)).copy(refused = false) }
        viewModelScope.launch {
            val reader = accounts.byId(readerId)
            if (reader == null || !send(reader)) {
                current.update { list.put(it, list.get(it)?.putBack(row, at)).copy(refused = true) }
            }
        }
    }

    private fun kept(account: Account) =
        Kept(account.id, account.bestDisplayName, account.qualifiedHandle, account.avatar)
}

/** One of the three lists, to take a row out of and put it back in. */
private class Rows<T>(val get: (BlockedState) -> List<T>?, val put: (BlockedState, List<T>?) -> BlockedState)

private val BLOCKED = Rows({ it.blocked }, { state, list -> state.copy(blocked = list) })
private val MUTED = Rows({ it.muted }, { state, list -> state.copy(muted = list) })
private val SERVERS = Rows({ it.servers }, { state, list -> state.copy(servers = list) })

/** [row] back at [at], unless it is there again already. */
private fun <T> List<T>.putBack(row: T, at: Int): List<T> =
    if (row in this) this else toMutableList().apply { add(at.coerceIn(0, size), row) }
