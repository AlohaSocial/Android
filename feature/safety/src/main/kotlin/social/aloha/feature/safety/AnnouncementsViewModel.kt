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
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.announcements.Announcements
import social.aloha.core.model.Announcement
import social.aloha.core.model.AnnouncementReaction
import social.aloha.core.model.SignedInAccount

@Immutable
internal data class AnnouncementsUiState(
    val announcements: List<Announcement> = emptyList(),
    /** Those unread when the screen opened, still marked new while it shows them. */
    val fresh: Set<String> = emptySet(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val reactionFailed: Boolean = false,
)

/**
 * The server's announcements. Those shown are marked read as the screen opens, and stay marked new
 * until it closes; a reaction shows at once and is taken back if the server refuses it.
 */
@HiltViewModel(assistedFactory = AnnouncementsViewModel.Factory::class)
internal class AnnouncementsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val announcements: Announcements,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): AnnouncementsViewModel
    }

    private val state = MutableStateFlow(AnnouncementsUiState())
    val uiState: StateFlow<AnnouncementsUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            reader = account
            when (val answer = announcements.all(account)) {
                is Answer.Got -> {
                    val unread = answer.value.filterNot { it.read }.map { it.id }
                    state.update { AnnouncementsUiState(answer.value, unread.toSet(), loading = false) }
                    unread.forEach { launch { announcements.read(account, it) } }
                }

                is Answer.Missed -> state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun onReact(id: String, name: String, add: Boolean) {
        val account = reader ?: return
        react(id, name, add)
        viewModelScope.launch {
            // only this reaction is taken back: another made meanwhile stays
            if (announcements.react(account, id, name, add) is Answer.Missed) {
                react(id, name, !add)
                state.update { it.copy(reactionFailed = true) }
            }
        }
    }

    private fun react(id: String, name: String, add: Boolean) = state.update {
        it.copy(announcements = it.announcements.map { a -> if (a.id == id) a.reacted(name, add) else a })
    }

    fun onReactionFailureShown() = state.update { it.copy(reactionFailed = false) }
}

/** [this] announcement with the reader's reaction [name] added, or taken back unless [add]. */
internal fun Announcement.reacted(name: String, add: Boolean): Announcement {
    val existing = reactions.firstOrNull { it.name == name }
    val changed = when {
        existing == null && add -> reactions + AnnouncementReaction(name, count = 1, me = true)

        existing == null || existing.me == add -> reactions

        else -> reactions.map {
            if (it.name == name) it.copy(count = it.count + if (add) 1 else -1, me = add) else it
        }
    }
    return copy(reactions = changed.filter { it.count > 0 })
}

/** How many announcements the active account has not read, for Home's banner. */
@HiltViewModel
internal class AnnouncementsBannerViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val announcements: Announcements,
) : ViewModel() {
    private val unread = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = unread.asStateFlow()

    /** Asks again; the banner calls it each time Home comes back, so one read elsewhere goes. */
    fun onRefresh() {
        viewModelScope.launch {
            // right after launch the active account may not be loaded yet
            val account = accounts.activeAccount.filterNotNull().first()
            val answer = announcements.all(account)
            unread.value = (answer as? Answer.Got)?.value?.count { !it.read } ?: 0
        }
    }
}
