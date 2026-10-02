// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountOrder
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ReauthRequest
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.sync.AccountSignOut
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.ConfirmDialog
import social.aloha.core.ui.SettingsSection

/** One signed-in account as Settings lists it. */
@Immutable
internal data class AccountEntry(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String?,
    val active: Boolean,
    val needsReauth: Boolean,
)

@HiltViewModel
internal class AccountsSettingsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val order: AccountOrder,
    private val signOuts: AccountSignOut,
    private val reauth: ReauthRequest,
) : ViewModel() {
    val entries: StateFlow<List<AccountEntry>> = combine(accounts.accounts, accounts.activeAccount) { all, active ->
        all.map {
            AccountEntry(it.id, it.displayName, it.qualifiedHandle, it.avatarUrl, it.id == active?.id, it.needsReauth)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), emptyList())

    /** Account [id] one place up or down the list, the order the switcher and the launcher show. */
    fun move(id: String, by: Int) {
        val ids = entries.value.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + by
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { order.reorder(ids) }
    }

    /** The account becomes the one in use, and its sign-in shows. */
    fun signInAgain(id: String) {
        viewModelScope.launch {
            accounts.activate(id)
            reauth.ask()
        }
    }

    fun signOut(id: String) {
        viewModelScope.launch { accounts.byId(id)?.let(signOuts::signOut) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object AccountsSection : SettingsSection {
    override val key: String = "accounts"
    override val order: Int = 10
    override val title: Int = R.string.accounts_title
    override val icon: ImageVector = AlohaIcons.Members

    @Composable
    override fun Content() {
        val viewModel: AccountsSettingsViewModel = hiltViewModel()
        val entries by viewModel.entries.collectAsStateWithLifecycle()
        AccountsContent(entries, viewModel::move, viewModel::signInAgain, viewModel::signOut)
    }
}

/** Each account with the order, a sign-in again where its server refused it, and signing it out. */
@Composable
internal fun AccountsContent(
    entries: List<AccountEntry>,
    onMove: (id: String, by: Int) -> Unit,
    onSignInAgain: (String) -> Unit,
    onSignOut: (String) -> Unit,
) {
    var leaving by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider()
            ListItem(
                leadingContent = { Avatar(entry.avatarUrl, AVATAR) },
                headlineContent = {
                    Text(entry.name.ifBlank { entry.handle }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        when {
                            entry.needsReauth -> stringResource(R.string.accounts_needs_reauth, entry.handle)
                            entry.active -> stringResource(R.string.accounts_in_use, entry.handle)
                            else -> entry.handle
                        },
                        color = if (entry.needsReauth) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailingContent = {
                    // buttons rather than a drag, so the order is as easy to change with TalkBack or a switch
                    Row {
                        IconButton(onClick = { onMove(entry.id, -1) }, enabled = index > 0) {
                            Icon(AlohaIcons.ExpandLess, stringResource(R.string.accounts_move_up, entry.handle))
                        }
                        IconButton(onClick = { onMove(entry.id, 1) }, enabled = index < entries.lastIndex) {
                            Icon(AlohaIcons.ExpandMore, stringResource(R.string.accounts_move_down, entry.handle))
                        }
                    }
                },
            )
            Row {
                if (entry.needsReauth) {
                    TextButton(onClick = { onSignInAgain(entry.id) }) {
                        Text(stringResource(R.string.accounts_sign_in_again))
                    }
                }
                TextButton(onClick = { leaving = entry.id }) { Text(stringResource(R.string.accounts_sign_out)) }
            }
        }
    }
    val going = entries.firstOrNull { it.id == leaving }
    if (going != null) {
        ConfirmDialog(
            title = stringResource(R.string.accounts_sign_out_title, going.handle),
            body = stringResource(R.string.accounts_sign_out_body),
            action = stringResource(R.string.accounts_sign_out),
            onDismiss = { leaving = null },
            onConfirm = { onSignOut(going.id) },
        )
    }
}

private val AVATAR = 40.dp
