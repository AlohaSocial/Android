// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.Trouble
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.data.trouble
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Card
import social.aloha.core.model.Reaction
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.StatusEdit
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.network.ApiError
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowCache
import social.aloha.core.ui.minuteTicks

/**
 * One post and the conversation around it. The stored copy of the post draws at once; the server's
 * conversation replaces the layout when it arrives, and every post in it is observed from the cache,
 * so an action here or anywhere else shows the moment it is stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
internal class ThreadViewModel @AssistedInject constructor(
    @Assisted private val key: ThreadKey,
    accounts: AccountRepository,
    private val threads: ThreadRepository,
    private val interactions: StatusInteractions,
    private val cache: RichTextCache,
    private val clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: ThreadKey): ThreadViewModel
    }

    private data class Control(
        val loading: Boolean = true,
        val trouble: Trouble? = null,
        val gone: Boolean = false,
        val card: Card? = null,
        val reactions: List<Reaction>? = null,
        val history: List<EditVersion>? = null,
        val actionFailed: Boolean = false,
        val archived: Boolean = false,
        val people: Map<StatusListKind, List<String?>> = emptyMap(),
    )

    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val lines = MutableStateFlow<List<ThreadLine>>(listOf(ThreadLine.Focused(key.statusId)))
    private val control = MutableStateFlow(Control())
    private val rows = StatusRowCache(cache, showContext = false)

    @Volatile private var stored: Map<String, Status> = emptyMap()

    /** The focused post with its extras, built again only when the post or they change. */
    private var focusedShown: Triple<Status, Control, Status>? = null

    private val account: StateFlow<SignedInAccount?> = accounts.accounts
        .map { all -> all.firstOrNull { it.id == key.readerId } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<ThreadUiState> = combine(account.filterNotNull(), lines) { account, lines ->
        account to lines
    }
        .flatMapLatest { (account, lines) ->
            combine(
                threads.observe(
                    account,
                    lines.mapNotNull {
                        it.statusId
                    },
                ),
                colors.filterNotNull(),
                control,
                minuteTicks(clock),
                ::Snapshot,
            )
                .map { snapshot ->
                    stored = snapshot.stored
                    state(account, lines, snapshot)
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ThreadUiState())

    init {
        viewModelScope.launch { load(account.filterNotNull().first()) }
        if (key.history) onHistory(open = true)
    }

    /** Rows are rendered with the theme's colours, which only the screen knows. */
    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    fun onRefresh() {
        viewModelScope.launch { account.value?.let { load(it) } }
    }

    fun onToggle(statusId: String, toggle: Toggle) = act(statusId) { account, status ->
        interactions.toggle(account, status, toggle)
    }

    /** Reacts to the focused post, shown at once and taken back if the server refuses. */
    fun onReact(name: String, add: Boolean) {
        val before = control.value.reactions.orEmpty()
        control.update { it.copy(reactions = ThreadPresentation.reacted(before, name, add)) }
        act(key.statusId) { account, status ->
            interactions.react(account, status.displayed.id, name, add)?.also {
                control.update { state -> state.copy(reactions = before) }
            }
        }
    }

    fun onVote(statusId: String, choices: List<Int>) = act(statusId) { account, status ->
        interactions.vote(account, status, choices)
    }

    fun onDelete(statusId: String) = act(statusId) { account, status ->
        interactions.delete(account, status).also {
            if (it == null && statusId == key.statusId) control.update { state -> state.copy(gone = true) }
        }
    }

    fun onArchive(statusId: String) = act(statusId) { account, status ->
        interactions.archive(account, status).also {
            if (it == null) control.update { state -> state.copy(archived = true) }
        }
    }

    /** The archive, or else the failure, the screen said; only that one, so the other still shows. */
    fun onNoticeShown(archived: Boolean) {
        control.update { if (archived) it.copy(archived = false) else it.copy(actionFailed = false) }
    }

    /** The focused post's edits, fetched for the sheet when [open]; the sheet gone otherwise. */
    fun onHistory(open: Boolean) {
        if (!open) return control.update { it.copy(history = null) }
        viewModelScope.launch {
            val account = account.filterNotNull().first()
            val colors = colors.filterNotNull().first()
            when (val answer = threads.history(account, key.statusId)) {
                // parsed once here, not on every redraw of the thread under the sheet
                is Answer.Got -> {
                    val versions =
                        withContext(Dispatchers.Default) { ThreadPresentation.versions(answer.value, colors) }
                    control.update { it.copy(history = versions) }
                }

                is Answer.Missed -> control.update { it.copy(actionFailed = true) }
            }
        }
    }

    private fun act(statusId: String, block: suspend (SignedInAccount, Status) -> ApiError?) {
        val status = stored[statusId] ?: return
        viewModelScope.launch {
            val account = account.value ?: return@launch
            if (block(account, status) != null) control.update { it.copy(actionFailed = true) }
        }
    }

    private suspend fun load(account: SignedInAccount) {
        control.update { it.copy(loading = true) }
        when (val answer = threads.load(account, key.statusId)) {
            is Answer.Got -> {
                val conversation = answer.value
                lines.value = ThreadShape.of(conversation.focused.id, conversation.ancestors, conversation.descendants)
                control.update { it.copy(loading = false, trouble = null) }
                loadExtras(account, conversation.focused)
            }

            is Answer.Missed -> control.update {
                it.copy(loading = false, gone = answer.error == ApiError.NotFound, trouble = answer.error.trouble)
            }
        }
    }

    /** What only the focused post shows: its card when the post carries none, who liked it, its reactions. */
    private fun loadExtras(account: SignedInAccount, focused: Status) {
        val shown = focused.displayed
        if (shown.card == null) {
            viewModelScope.launch {
                threads.card(account, shown.id)?.let { card -> control.update { it.copy(card = card) } }
            }
        }
        // the first few who favourited and boosted it, drawn beside the counts: those seen last, then the server's
        listOfNotNull(
            StatusListKind.FavouritedBy.takeIf { shown.favouritesCount > 0 },
            StatusListKind.BoostedBy.takeIf { shown.reblogsCount > 0 },
        ).forEach { kind ->
            viewModelScope.launch {
                threads.people(account, shown.id, boosts = kind == StatusListKind.BoostedBy, limit = PEOPLE)
                    .collect { people ->
                        control.update {
                            it.copy(
                                people =
                                    it.people + (kind to people.map { p -> p.avatar }),
                            )
                        }
                    }
            }
        }
        val capabilities = account.capabilities
        if (capabilities.emojiReactions || capabilities.isNextcloudSocial) {
            viewModelScope.launch {
                (threads.reactions(account, shown.id) as? Answer.Got)?.let { answer ->
                    control.update { it.copy(reactions = answer.value) }
                }
            }
        }
    }

    /** One moment of the thread: what is stored, how it is drawn, and what the screen is doing. */
    private data class Snapshot(
        val stored: Map<String, Status>,
        val colors: RichTextColors,
        val control: Control,
        val now: Instant,
    )

    private fun state(account: SignedInAccount, lines: List<ThreadLine>, snapshot: Snapshot): ThreadUiState {
        val control = snapshot.control
        val focused = snapshot.stored[key.statusId]?.let { withExtras(it, control) }
        rows.use(snapshot.colors)
        val shown = focused?.let { snapshot.stored + (key.statusId to it) } ?: (snapshot.stored - key.statusId)
        return ThreadUiState(
            items = ThreadPresentation.items(lines, shown, rows, account.serverAccountId),
            loading = control.loading,
            trouble = control.trouble,
            gone = control.gone || (!control.loading && focused == null),
            now = snapshot.now,
            lists = focused?.let { ThreadPresentation.lists(it.displayed, account.capabilities) }.orEmpty(),
            edited = focused?.displayed?.isEdited == true,
            canReact = account.capabilities.let { it.emojiReactions || it.isNextcloudSocial },
            albums = account.capabilities.collections,
            archive = account.capabilities.isNextcloudSocial,
            archived = control.archived,
            history = control.history,
            actionFailed = control.actionFailed,
            people = control.people,
            readerAvatar = account.avatarUrl,
            application = focused?.displayed?.application,
        )
    }

    private fun withExtras(status: Status, control: Control): Status {
        focusedShown?.let { (source, extras, shown) ->
            if (source === status && extras.card == control.card && extras.reactions == control.reactions) return shown
        }
        return ThreadPresentation.withExtras(status, control.card, control.reactions).also {
            focusedShown = Triple(status, control, it)
        }
    }

    private companion object {
        const val PEOPLE = 4
        const val STOP_MILLIS = 5_000L
    }
}
