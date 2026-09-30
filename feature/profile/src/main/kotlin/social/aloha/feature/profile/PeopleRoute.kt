// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.CustomEmoji
import social.aloha.core.navigation.PeopleKey
import social.aloha.core.navigation.PeopleKind
import social.aloha.core.ui.AccountRow
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.NearEndEffect
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

@Immutable
internal data class PeopleUiState(
    val people: List<Person> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val reachedEnd: Boolean = false,
) {
    @Immutable
    data class Person(val author: StatusRowUi.AuthorUi, val emojis: List<CustomEmoji>)
}

/** An account's followers or following, a page at a time. */
@HiltViewModel(assistedFactory = PeopleViewModel.Factory::class)
internal class PeopleViewModel @AssistedInject constructor(
    @Assisted private val key: PeopleKey,
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val cache: RichTextCache,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: PeopleKey): PeopleViewModel
    }

    private val state = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = state.asStateFlow()
    private var next: HttpUrl? = null
    private var mapper: StatusRowMapper? = null

    /** The accounts shown, kept so other colours can draw them again. */
    private var shown: List<Account> = emptyList()

    /** The first page waits for the screen's colours, which names are drawn with; new ones redraw them. */
    fun onColors(colors: RichTextColors) {
        val first = mapper == null
        val drawn = StatusRowMapper(cache, colors).also { mapper = it }
        if (first) loadMore() else state.update { it.copy(people = shown.map { account -> person(account, drawn) }) }
    }

    fun onNearEnd() {
        val current = state.value
        if (!current.loading && !current.reachedEnd && !current.failed) loadMore()
    }

    fun onRetry() {
        state.update { it.copy(failed = false) }
        loadMore()
    }

    private fun loadMore() {
        val mapper = mapper ?: return
        state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId)
            val answer = reader?.let {
                profiles.people(it, key.accountId, followers = key.kind == PeopleKind.Followers, next)
            }
            state.update { current ->
                when (answer) {
                    is Answer.Got -> {
                        next = answer.value.next
                        val known = current.people.mapTo(HashSet()) { it.author.id }
                        val fresh = answer.value.accounts.filter { it.id !in known }
                        shown = shown + fresh
                        val added = fresh.map { person(it, mapper) }
                        current.copy(
                            people = current.people + added,
                            loading = false,
                            reachedEnd =
                                answer.value.next == null,
                        )
                    }

                    else -> current.copy(loading = false, failed = true)
                }
            }
        }
    }

    private fun person(account: Account, mapper: StatusRowMapper) =
        PeopleUiState.Person(mapper.author(account), account.emojis)
}

@Composable
public fun PeopleRoute(key: PeopleKey, navigation: ProfileNavigation, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<PeopleViewModel, PeopleViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    PeopleScreen(
        key.kind,
        state,
        onBack = navigation::back,
        onOpen = { navigation.openProfile(it, null) },
        onNearEnd = viewModel::onNearEnd,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleScreen(
    kind: PeopleKind,
    state: PeopleUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNearEnd: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        if (kind ==
            PeopleKind.Followers
        ) {
            R.string.profile_followers_title
        } else {
            R.string.profile_following_title
        },
    )
    val listState = rememberLazyListState()
    NearEndEffect(listState, state.people.size, onNearEnd)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.profile_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize()) {
            items(state.people, key = { it.author.id }) { person ->
                AccountRow(person.author, person.emojis, onOpen = { onOpen(person.author.id) })
                HorizontalDivider()
            }
            item(key = "footer") {
                when {
                    state.failed -> Column(
                        Modifier.fillMaxWidth().padding(AlohaSpacing.l),
                        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.profile_people_error), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onRetry) { Text(stringResource(R.string.profile_retry)) }
                    }

                    state.loading -> ListProgress()

                    state.people.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(AlohaSpacing.l),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.profile_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
