// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.moderation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.Authorization
import social.aloha.core.data.OAuthCallbackInbox
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInResult
import social.aloha.core.data.moderation.Moderation
import social.aloha.core.model.AdminAccount
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.model.AdminLink
import social.aloha.core.model.AdminReport
import social.aloha.core.model.AdminTag
import social.aloha.core.model.ModeratorRole
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.AdminAccountEndpoints.Origin
import social.aloha.core.network.endpoints.AdminAccountEndpoints.Standing
import social.aloha.core.network.endpoints.ModerationEndpoints.TrendKind

internal enum class ModerationTab { Reports, Accounts, Trends }

/**
 * The console for one moderator. Each list is null until it arrived. [consent] while the account's token
 * does not carry the admin scopes yet: the server answered 403, and a second authorisation asks for them.
 * [refused] after the server would not take a decision, which then stands as it was.
 */
@Immutable
internal data class ModerationState(
    val role: ModeratorRole? = null,
    val consent: Boolean = false,
    val authorizing: Boolean = false,
    val resolvedShown: Boolean = false,
    val reports: List<AdminReport>? = null,
    val standing: Standing = Standing.Active,
    val origin: Origin = Origin.Any,
    val username: String = "",
    val accounts: List<AdminAccount>? = null,
    val tags: List<AdminTag>? = null,
    val posts: List<Status>? = null,
    val links: List<AdminLink>? = null,
    val failed: Boolean = false,
    val refused: Boolean = false,
    /** The authorisation page to open in the browser, once. */
    val open: String? = null,
) {
    val tabs: List<ModerationTab>
        get() = listOfNotNull(
            ModerationTab.Reports.takeIf { role?.reports == true },
            ModerationTab.Accounts.takeIf { role?.accounts == true },
            ModerationTab.Trends.takeIf { role?.trends == true },
        )
}

@HiltViewModel(assistedFactory = ModerationViewModel.Factory::class)
internal class ModerationViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val moderation: Moderation,
    private val coordinator: SignInCoordinator,
    private val inbox: OAuthCallbackInbox,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): ModerationViewModel
    }

    private val current = MutableStateFlow(ModerationState())
    val state: StateFlow<ModerationState> = current.asStateFlow()
    private val lists = ListLoader(moderation, current)

    init {
        load()
        // the browser hands the moderator's authorisation back here; a sign-in's is not ours to take
        viewModelScope.launch {
            inbox.callback.filterNotNull().collect { uri ->
                if (!current.value.authorizing) return@collect
                val result = coordinator.complete(uri)
                if (result == SignInResult.NotForUs) return@collect
                inbox.consume(uri)
                val granted = result is SignInResult.SignedIn
                current.update { it.copy(authorizing = false, consent = false, refused = !granted) }
                if (granted) load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            val reader = accounts.byId(readerId) ?: return@launch
            current.update { it.copy(failed = false) }
            val role = moderation.role(reader)
            current.update { it.copy(role = role, failed = role == null) }
            if (role == null) return@launch
            if (role.reports) launch { lists.reports(reader) }
            if (role.accounts) launch { lists.accounts(reader) }
            if (role.trends) launch { lists.trends(reader) }
        }
    }

    fun showResolved(resolved: Boolean) {
        current.update { it.copy(resolvedShown = resolved, reports = null) }
        withReader { lists.reports(it) }
    }

    /** Which accounts show: of a standing, local or remote, whose username starts as typed. */
    fun findAccounts(
        standing: Standing = current.value.standing,
        origin: Origin = current.value.origin,
        username: String = current.value.username,
    ) {
        current.update { it.copy(standing = standing, origin = origin, username = username, accounts = null) }
        withReader { lists.accounts(it) }
    }

    /** Takes the report, or gives it back; the row shows what the server answered. */
    fun assign(id: String, mine: Boolean) = withReader { reader ->
        lists.report(id, moderation.assign(reader, id, mine))
    }

    /** A resolved report leaves the unresolved queue, a reopened one the resolved list. */
    fun resolve(id: String, resolved: Boolean) = drop(REPORTS, { it.id == id }) { reader ->
        moderation.resolve(reader, id, resolved) is Answer.Got
    }

    /** Silences or suspends the account a report is about, and resolves the report with it. */
    fun actOnReport(report: AdminReport, action: AdminAccountAction) {
        val target = report.targetAccount?.id ?: return
        drop(REPORTS, { it.id == report.id }) { reader ->
            moderation.act(reader, target, action, reportId = report.id)
        }
    }

    /** Silences or suspends an account; it leaves the list of the standing it had. */
    fun act(id: String, action: AdminAccountAction) = drop(ACCOUNTS, { it.id == id }) { reader ->
        moderation.act(reader, id, action)
    }

    /** Lifts what the account's standing says: the silence, the suspension or the forced warning. */
    fun lift(account: AdminAccount) = drop(ACCOUNTS, { it.id == account.id }) { reader ->
        val answer = when {
            account.suspended -> moderation.unsuspend(reader, account.id)
            account.silenced -> moderation.unsilence(reader, account.id)
            else -> moderation.unsensitive(reader, account.id)
        }
        answer is Answer.Got
    }

    fun review(kind: TrendKind, id: String, approve: Boolean) {
        val send: suspend (SignedInAccount) -> Boolean = { reader -> moderation.review(reader, kind, id, approve) }
        when (kind) {
            TrendKind.Tags -> drop(TAGS, { it.id == id }, send)
            TrendKind.Links -> drop(LINKS, { it.id == id }, send)
            TrendKind.Statuses -> drop(POSTS, { it.id == id }, send)
        }
    }

    /** Asks the server for the admin scopes; the browser comes back through the inbox. */
    fun allow() = withReader { reader ->
        current.update { it.copy(authorizing = true) }
        when (val started = coordinator.beginModeration(reader)) {
            is Authorization.Started -> current.update { it.copy(open = started.url) }
            is Authorization.Failed -> current.update { it.copy(authorizing = false, refused = true) }
        }
    }

    fun onOpened() {
        current.update { it.copy(open = null) }
    }

    fun onRefusalShown() {
        current.update { it.copy(refused = false) }
    }

    /**
     * The row [matches] picks leaves [list] at once; where the server refused, it alone comes back where it
     * was, and that is said. Whatever else changed meanwhile stays.
     */
    private fun <T> drop(list: Rows<T>, matches: (T) -> Boolean, send: suspend (SignedInAccount) -> Boolean) {
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

    private fun withReader(block: suspend (SignedInAccount) -> Unit) {
        viewModelScope.launch { accounts.byId(readerId)?.let { block(it) } }
    }
}

/** One of the console's lists, to take a row out of and put it back in. */
internal class Rows<T>(val get: (ModerationState) -> List<T>?, val put: (ModerationState, List<T>?) -> ModerationState)

private val REPORTS = Rows({ it.reports }, { state, list -> state.copy(reports = list) })
private val ACCOUNTS = Rows({ it.accounts }, { state, list -> state.copy(accounts = list) })
private val TAGS = Rows({ it.tags }, { state, list -> state.copy(tags = list) })
private val LINKS = Rows({ it.links }, { state, list -> state.copy(links = list) })
private val POSTS = Rows({ it.posts }, { state, list -> state.copy(posts = list) })

/** [row] back at [at], unless it is there again already. */
internal fun <T> List<T>.putBack(row: T, at: Int): List<T> =
    if (row in this) this else toMutableList().apply { add(at.coerceIn(0, size), row) }
