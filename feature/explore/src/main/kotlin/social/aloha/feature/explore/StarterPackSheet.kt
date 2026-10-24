// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Account
import social.aloha.core.ui.Skeleton
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusRowMapper

/**
 * A starter pack opened: its name and where it comes from at once, then its members, each marked as
 * followed or with a follow of their own, and the handles the server could not reach yet. Following the
 * pack follows the rest of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StarterPackSheet(
    sheet: PackSheet,
    state: ExploreUiState,
    mapper: StatusRowMapper,
    actions: ExploreActions,
    rowActions: StatusActions,
    snackbars: SnackbarHostState,
) {
    val pack = sheet.pack
    // the colour of the account rows, which draw their own background
    ModalBottomSheet(onDismissRequest = actions::onClosePack, containerColor = MaterialTheme.colorScheme.surface) {
        Box(Modifier.semantics { paneTitle = pack.name }) {
            LazyColumn(Modifier.fillMaxWidth()) {
                item(key = "header") { Header(sheet, state, actions) }
                when (val members = sheet.members) {
                    Load.Waiting -> item(key = "waiting") { Skeleton(rows = WAITING_ROWS) }

                    is Load.Failed -> item(key = "failed") {
                        Failed(members.trouble, actions::onRetryPack, Modifier.fillMaxWidth())
                    }

                    is Load.Loaded -> {
                        items(members.value.accounts, key = { "m:${it.id}" }) { member ->
                            Person(member, mapper, rowActions) { Member(member, sheet, state, actions) }
                        }
                        unreachable(members.value.unresolved, actions)
                    }
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Header(sheet: PackSheet, state: ExploreUiState, actions: ExploreActions) {
    val pack = sheet.pack
    Column(
        Modifier.padding(horizontal = AlohaSpacing.m).padding(bottom = AlohaSpacing.s),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        Text(pack.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(
            packLine(pack),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pack.url?.let { url ->
            TextButton(onClick = { actions.onWeb(url) }) { Text(stringResource(R.string.explore_pack_source)) }
        }
        FollowAll(sheet, state, actions)
    }
}

/** Follows the pack: all of it, or the rest where the reader follows some already; nothing once all. */
@Composable
private fun FollowAll(sheet: PackSheet, state: ExploreUiState, actions: ExploreActions) {
    val loaded = (sheet.members as? Load.Loaded)?.value
    val others = loaded?.accounts.orEmpty().filter { it.id != state.viewer }
    val followed = others.count { state.following[it.id] == true || it.id in sheet.requested }
    // the server fetches an unreachable member when the pack is followed, so it is never all followed
    val everyone = others.isNotEmpty() && followed == others.size
    if (everyone && loaded?.unresolved?.isEmpty() == true) {
        Text(stringResource(R.string.explore_pack_all_followed), style = MaterialTheme.typography.labelLarge)
    } else {
        Button(onClick = { actions.onFollowAll(sheet.pack.slug) }, enabled = !sheet.followingAll) {
            Text(stringResource(if (followed > 0) R.string.explore_follow_rest else R.string.explore_follow_all))
        }
    }
}

/** The handles the server could not reach yet, with a retry. */
private fun LazyListScope.unreachable(handles: List<String>, actions: ExploreActions) {
    if (handles.isEmpty()) return
    item(key = "unreachable") {
        Column(
            Modifier.fillMaxWidth().padding(AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Text(
                stringResource(R.string.explore_pack_unreachable),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.explore_pack_unreachable_why),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            handles.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = actions::onRetryPack) { Text(stringResource(R.string.explore_retry)) }
        }
    }
}

@Composable
private fun Member(member: Account, sheet: PackSheet, state: ExploreUiState, actions: ExploreActions) {
    val said = when {
        member.id == state.viewer -> return
        state.following[member.id] == true -> R.string.explore_member_following
        member.id in sheet.requested -> R.string.explore_member_requested
        else -> null
    }
    if (said != null) {
        Text(
            stringResource(said),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = AlohaSpacing.m),
        )
        return
    }
    val label = stringResource(R.string.explore_member_follow_label, member.bestDisplayName)
    OutlinedButton(
        onClick = { actions.onFollowMember(member) },
        enabled = member.id !in sheet.pending && !sheet.followingAll,
        modifier = Modifier.padding(end = AlohaSpacing.m).semantics { contentDescription = label },
    ) { Text(stringResource(R.string.explore_member_follow)) }
}

private const val WAITING_ROWS = 3
