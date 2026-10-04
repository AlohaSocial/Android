// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import social.aloha.core.data.profile.ListChoice
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.ConfirmDialog
import social.aloha.core.ui.ListProgress

/**
 * The profile menu's items about the reader and the account: how a follow behaves, muting and
 * blocking, their whole server, the reader's note, lists, and whether they may follow the reader.
 */
@Composable
internal fun RelationItems(
    relation: Relation,
    domain: String?,
    actions: ProfileScreenActions,
    ask: (Asking) -> Unit,
    close: () -> Unit,
) {
    @Composable
    fun item(label: Int, checked: Boolean? = null, onClick: () -> Unit) = Item(label, checked) {
        close()
        onClick()
    }
    if (relation.following) {
        item(R.string.profile_show_boosts, relation.showingReblogs) {
            actions.onChange(RelationshipChange.Follow(!relation.showingReblogs, relation.notifying))
        }
        item(R.string.profile_notify, relation.notifying) {
            actions.onChange(RelationshipChange.Follow(relation.showingReblogs, !relation.notifying))
        }
        item(R.string.profile_lists) {
            actions.onLists()
            ask(Asking.Lists)
        }
    }
    if (relation.muting) {
        item(R.string.profile_unmute) { actions.onChange(RelationshipChange.Unmute) }
    } else {
        item(R.string.profile_mute) { ask(Asking.Mute) }
    }
    if (relation.blocking) {
        item(R.string.profile_unblock) { actions.onChange(RelationshipChange.Unblock) }
    } else {
        item(R.string.profile_block) { ask(Asking.Block) }
    }
    if (domain != null) {
        if (relation.domainBlocking) {
            item(R.string.profile_unblock_domain) { actions.onBlockDomain(false) }
        } else {
            item(R.string.profile_block_domain) { ask(Asking.BlockDomain) }
        }
    }
    if (relation.followedBy) item(R.string.profile_remove_follower) { ask(Asking.RemoveFollower) }
}

/** A menu item, with a checkbox when it switches something on or off. */
@Composable
private fun Item(label: Int, checked: Boolean?, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        trailingIcon = checked?.let { { Checkbox(checked = it, onCheckedChange = null) } },
        modifier = if (checked == null) {
            Modifier
        } else {
            Modifier.semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            }
        },
    )
}

/** The dialog for what the profile is [asking], for account [handle] on server [domain]. */
@Composable
internal fun RelationDialog(
    asking: Asking,
    handle: String,
    domain: String?,
    state: ProfileUiState,
    actions: ProfileScreenActions,
    onDone: () -> Unit,
) {
    val confirm = { change: RelationshipChange -> actions.onChange(change) }
    when (asking) {
        Asking.Block -> ConfirmDialog(
            stringResource(R.string.profile_block_title, handle),
            stringResource(R.string.profile_block_body),
            stringResource(R.string.profile_block_confirm),
            onDone,
        ) { confirm(RelationshipChange.Block) }

        Asking.Mute -> MuteDialog(handle, onDone) {
            onDone()
            confirm(it)
        }

        Asking.BlockDomain -> ConfirmDialog(
            stringResource(R.string.profile_block_domain_title, domain.orEmpty()),
            stringResource(R.string.profile_block_domain_body),
            stringResource(R.string.profile_block_confirm),
            onDone,
        ) { actions.onBlockDomain(true) }

        Asking.RemoveFollower -> ConfirmDialog(
            stringResource(R.string.profile_remove_follower_title, handle),
            stringResource(R.string.profile_remove_follower_body),
            stringResource(R.string.profile_remove_follower_confirm),
            onDone,
        ) { confirm(RelationshipChange.RemoveFollower) }

        Asking.Lists -> ListsDialog(state.lists, actions::onListed, onDone)
    }
}

/** For how long, and whether their notifications go quiet too. */
@Composable
private fun MuteDialog(handle: String, onDismiss: () -> Unit, onMute: (RelationshipChange.Mute) -> Unit) {
    var seconds by rememberSaveable { mutableStateOf<Long?>(null) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_mute_title, handle)) },
        text = {
            Column {
                MUTE_LENGTHS.forEach { (length, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(seconds == length, role = Role.RadioButton) {
                            seconds = length
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = seconds == length, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = AlohaSpacing.s))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = AlohaSpacing.s)
                        .toggleable(notifications, role = Role.Checkbox) { notifications = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = notifications, onCheckedChange = null)
                    Text(stringResource(R.string.profile_mute_notifications), Modifier.padding(start = AlohaSpacing.s))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMute(RelationshipChange.Mute(notifications, seconds)) }) {
                Text(stringResource(R.string.profile_mute))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_cancel)) } },
    )
}

/** The reader's lists, each ticked when the account is on it; a tick changes it at once. */
@Composable
private fun ListsDialog(lists: List<ListChoice>?, onListed: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_lists)) },
        text = {
            when {
                lists == null -> ListProgress()

                lists.isEmpty() -> Text(stringResource(R.string.profile_lists_none))

                else -> Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(AlohaSpacing.xxs)) {
                    lists.forEach { choice ->
                        // a list that follows a Nextcloud group holds the group's members, not the reader's choice
                        val group = choice.list.followsGroup
                        Row(
                            Modifier.fillMaxWidth().toggleable(choice.member, enabled = !group, role = Role.Checkbox) {
                                onListed(choice.list.id, it)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = choice.member, onCheckedChange = null, enabled = !group)
                            Column(Modifier.padding(start = AlohaSpacing.s)) {
                                Text(choice.list.title)
                                if (group) {
                                    Text(
                                        stringResource(R.string.profile_lists_group),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_done)) } },
    )
}

private val MUTE_LENGTHS = listOf(
    null to R.string.profile_mute_forever,
    3_600L to R.string.profile_mute_hour,
    86_400L to R.string.profile_mute_day,
    604_800L to R.string.profile_mute_week,
)
