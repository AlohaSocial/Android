// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.notifications.NotificationFiltering
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.NotificationPolicy
import social.aloha.core.model.PolicyDecision
import social.aloha.core.navigation.NotificationPolicyKey
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.readingColumn

/** The five rules, each read from the policy and written back into it. */
private enum class Rule(
    val title: Int,
    val read: (NotificationPolicy) -> PolicyDecision,
    val write: (NotificationPolicy, PolicyDecision) -> NotificationPolicy,
) {
    NotFollowing(R.string.policy_not_following, { it.forNotFollowing }, { p, d -> p.copy(forNotFollowing = d) }),
    NotFollowers(R.string.policy_not_followers, { it.forNotFollowers }, { p, d -> p.copy(forNotFollowers = d) }),
    NewAccounts(R.string.policy_new_accounts, { it.forNewAccounts }, { p, d -> p.copy(forNewAccounts = d) }),
    PrivateMentions(
        R.string.policy_private_mentions,
        { it.forPrivateMentions },
        { p, d -> p.copy(forPrivateMentions = d) },
    ),
    LimitedAccounts(
        R.string.policy_limited_accounts,
        { it.forLimitedAccounts },
        { p, d -> p.copy(forLimitedAccounts = d) },
    ),
}

/** [policy] is null while it loads; [dropFilters] on a server that keeps ignored notifications as filtered. */
internal data class PolicyUiState(
    val policy: NotificationPolicy? = null,
    val failed: Boolean = false,
    val saveFailed: Boolean = false,
    val dropFilters: Boolean = false,
)

@HiltViewModel(assistedFactory = PolicyViewModel.Factory::class)
internal class PolicyViewModel @AssistedInject constructor(
    @Assisted private val key: NotificationPolicyKey,
    private val accounts: AccountRepository,
    private val filtering: NotificationFiltering,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: NotificationPolicyKey): PolicyViewModel
    }

    private val state = MutableStateFlow(PolicyUiState())
    val uiState: StateFlow<PolicyUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId) ?: return@launch
            val answer = filtering.policy(reader)
            state.value = PolicyUiState(
                policy = (answer as? Answer.Got)?.value,
                failed = answer is Answer.Missed,
                dropFilters = reader.capabilities.isNextcloudSocial,
            )
        }
    }

    /** Shows the choice at once, and takes it back when the server refuses it. */
    fun onDecision(rule: Int, decision: PolicyDecision) {
        val before = state.value.policy ?: return
        val chosen = Rule.entries[rule].write(before, decision)
        state.update { it.copy(policy = chosen, saveFailed = false) }
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId) ?: return@launch
            when (val answer = filtering.setPolicy(reader, chosen)) {
                is Answer.Got -> state.update { it.copy(policy = answer.value) }
                is Answer.Missed -> state.update { it.copy(policy = before, saveFailed = true) }
            }
        }
    }
}

@Composable
public fun PolicyRoute(key: NotificationPolicyKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<PolicyViewModel, PolicyViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PolicyScreen(state, viewModel::onDecision, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PolicyScreen(
    state: PolicyUiState,
    onDecision: (rule: Int, decision: PolicyDecision) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.policy_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.notifications_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).readingColumn().verticalScroll(rememberScrollState())) {
            if (state.saveFailed) TroubleStrip(stringResource(R.string.policy_save_failed))
            val policy = state.policy
            when {
                state.failed -> Text(stringResource(R.string.policy_error), Modifier.padding(AlohaSpacing.m))
                policy == null -> ListProgress()
                else -> Rules(policy, state.dropFilters, onDecision)
            }
        }
    }
}

@Composable
private fun Rules(policy: NotificationPolicy, dropFilters: Boolean, onDecision: (Int, PolicyDecision) -> Unit) {
    Text(
        stringResource(R.string.policy_intro),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(AlohaSpacing.m),
    )
    val options = listOf(
        PolicyDecision.Accept to stringResource(R.string.policy_accept),
        PolicyDecision.Filter to stringResource(R.string.policy_filter),
        PolicyDecision.Drop to stringResource(R.string.policy_drop),
    )
    Rule.entries.forEachIndexed { index, rule ->
        ChoiceRows(stringResource(rule.title), options, rule.read(policy)) { onDecision(index, it) }
    }
    if (dropFilters) {
        Text(
            stringResource(R.string.policy_drop_filters),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(AlohaSpacing.m),
        )
    }
}
