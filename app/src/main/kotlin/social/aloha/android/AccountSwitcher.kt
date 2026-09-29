// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.Avatar

/**
 * The account in use, as an avatar in the top bar, and the sheet behind it: every signed-in account
 * to switch to, the reader's own profile, another account to add, and signing out of this one.
 */
@Composable
internal fun AccountSwitcher(viewModel: AppViewModel, onProfile: () -> Unit) {
    val accounts by viewModel.switcher.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val active = accounts.firstOrNull { it.active } ?: return
    val label = stringResource(R.string.accounts_button, active.handle)
    IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
        Avatar(active.avatarUrl, BUTTON_AVATAR)
    }
    if (open) {
        AccountSheet(
            accounts,
            onSwitch = {
                open = false
                viewModel.switchTo(it)
            },
            onProfile = {
                open = false
                onProfile()
            },
            onAdd = {
                open = false
                viewModel.addAccount()
            },
            onSignOut = {
                open = false
                viewModel.signOut()
            },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSheet(
    accounts: List<SwitcherAccount>,
    onSwitch: (String) -> Unit,
    onProfile: () -> Unit,
    onAdd: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val active = accounts.firstOrNull { it.active } ?: return
    var confirming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding()) {
            item {
                Text(
                    stringResource(R.string.accounts_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs).semantics {
                        heading()
                    },
                )
            }
            items(accounts, key = { it.id }) { account -> AccountLine(account) { onSwitch(account.id) } }
            item { HorizontalDivider(Modifier.padding(vertical = AlohaSpacing.xs)) }
            item { Action(AlohaIcons.Profile, stringResource(R.string.accounts_profile), onProfile) }
            item { Action(AlohaIcons.AddAccount, stringResource(R.string.accounts_add), onAdd) }
            item {
                Action(AlohaIcons.SignOut, stringResource(R.string.accounts_sign_out, active.handle)) {
                    confirming =
                        true
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.accounts_sign_out_title, active.handle)) },
            text = { Text(stringResource(R.string.accounts_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onSignOut()
                }) { Text(stringResource(R.string.accounts_sign_out_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.accounts_cancel)) }
            },
        )
    }
}

/** An account to switch to; the one in use says so in words as well as with its check. */
@Composable
private fun AccountLine(account: SwitcherAccount, onSwitch: () -> Unit) {
    val inUse = stringResource(R.string.accounts_active)
    val needsReauth = stringResource(R.string.accounts_needs_reauth)
    ListItem(
        modifier = Modifier
            .then(if (account.active) Modifier else Modifier.clickable(role = Role.Button, onClick = onSwitch))
            .semantics {
                selected = account.active
                if (account.active) stateDescription = inUse
            },
        leadingContent = { Avatar(account.avatarUrl, LINE_AVATAR) },
        headlineContent = { Text(account.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(account.handle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (account.needsReauth) Text(needsReauth, color = MaterialTheme.colorScheme.error)
            }
        },
        colors = SheetRow,
        trailingContent = { if (account.active) Icon(AlohaIcons.Check, contentDescription = null) },
    )
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        colors = SheetRow,
    )
}

// rows sit on the sheet's own surface rather than painting one of theirs
private val SheetRow: ListItemColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

private val BUTTON_AVATAR = 32.dp
private val LINE_AVATAR = 40.dp
