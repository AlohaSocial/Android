// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.rememberHaptics

/**
 * The account in use, as an avatar in the top bar, and the sheet behind it: every signed-in account
 * to switch to, the reader's own profile, another account to add, and signing out of this one.
 */
@Composable
internal fun AccountSwitcher(viewModel: AppViewModel, links: AccountLinks) {
    val accounts by viewModel.switcher.collectAsStateWithLifecycle()
    val focusing: FocusViewModel = hiltViewModel()
    val focusOn by focusing.on.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val active = accounts.firstOrNull { it.active } ?: return
    val label = stringResource(R.string.accounts_button, active.handle)
    val next = accounts.getOrNull((accounts.indexOf(active) + 1) % accounts.size)?.takeIf { it != active }
    val haptics = rememberHaptics()
    val switchLabel = next?.let { stringResource(R.string.accounts_switch_to, it.handle) }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = switchLabel,
                onLongClick = next?.let {
                    {
                        haptics(HapticFeedbackType.LongPress)
                        viewModel.switchTo(it.id)
                    }
                },
                onClick = { open = true },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Avatar(active.avatarUrl, BUTTON_AVATAR)
    }
    if (open) {
        AccountSheet(
            accounts,
            onSwitch = {
                open = false
                viewModel.switchTo(it)
            },
            // each place closes the sheet as it opens
            links = AccountLinks {
                open = false
                links.open(it)
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
            focus = focusOn,
            onFocus = focusing::onFocus,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSheet(
    accounts: List<SwitcherAccount>,
    onSwitch: (String) -> Unit,
    links: AccountLinks,
    onAdd: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    focus: Boolean = false,
    onFocus: ((Boolean) -> Unit)? = null,
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
            items(AccountPlace.entries.filter { it !in NEXTCLOUD_ONLY || active.nextcloudSocial }) { place ->
                val (icon, text) = place.look
                Action(icon, stringResource(text)) { links.open(place) }
            }
            onFocus?.let { item { FocusRow(focus, it) } }
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

/** Focus mode, saying what it changes. */
@Composable
private fun FocusRow(on: Boolean, onFocus: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(on, role = Role.Switch, onValueChange = onFocus),
        leadingContent = { Icon(AlohaIcons.Focus, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.focus_title)) },
        supportingContent = { Text(stringResource(R.string.focus_summary)) },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    )
}

/** An account to switch to; the one in use says so in words as well as with its check. */
@Composable
private fun AccountLine(account: SwitcherAccount, onSwitch: () -> Unit) {
    val inUse = stringResource(R.string.accounts_active)
    val needsReauth = stringResource(R.string.accounts_needs_reauth)
    val unread = stringResource(UiR.string.accounts_unread)
    ListItem(
        modifier = Modifier
            .then(if (account.active) Modifier else Modifier.clickable(role = Role.Button, onClick = onSwitch))
            .semantics {
                selected = account.active
                when {
                    account.active -> stateDescription = inUse
                    account.unread > 0 -> stateDescription = unread
                }
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
        trailingContent = {
            when {
                account.active -> Icon(AlohaIcons.Check, contentDescription = null)
                account.unread > 0 -> Badge()
            }
        },
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

/** A place's icon and name in the sheet. */
private val AccountPlace.look: Pair<ImageVector, Int>
    get() = when (this) {
        AccountPlace.Profile -> AlohaIcons.Profile to R.string.accounts_profile
        AccountPlace.Messages -> AlohaIcons.VisibilityDirect to R.string.accounts_messages
        AccountPlace.Bookmarks -> AlohaIcons.Bookmark to R.string.accounts_bookmarks
        AccountPlace.Favourites -> AlohaIcons.Favourite to R.string.accounts_favourites
        AccountPlace.Archived -> AlohaIcons.Archived to R.string.accounts_archived
        AccountPlace.Statistics -> AlohaIcons.Statistics to R.string.accounts_statistics
        AccountPlace.Lists -> AlohaIcons.Lists to R.string.accounts_lists
        AccountPlace.Hashtags -> AlohaIcons.Hashtag to R.string.accounts_hashtags
        AccountPlace.Filters -> AlohaIcons.Filtered to R.string.accounts_filters
        AccountPlace.Interests -> AlohaIcons.Explore to R.string.accounts_interests
        AccountPlace.Announcements -> AlohaIcons.News to R.string.accounts_announcements
        AccountPlace.Settings -> AlohaIcons.Settings to R.string.accounts_settings
    }

/** The places only Nextcloud Social keeps. */
private val NEXTCLOUD_ONLY = setOf(AccountPlace.Archived, AccountPlace.Statistics, AccountPlace.Interests)

private val BUTTON_AVATAR = 32.dp
private val LINE_AVATAR = 40.dp
