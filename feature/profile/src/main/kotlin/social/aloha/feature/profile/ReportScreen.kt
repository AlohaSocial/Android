// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.navigation.ReportKey

/** A report about the account of [key]; [onDone] leaves it. */
@Composable
public fun ReportRoute(key: ReportKey, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ReportViewModel, ReportViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportScreen(key.handle, key.remote, state, viewModel, onDone, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportScreen(
    handle: String,
    remote: Boolean,
    state: ReportUiState,
    actions: ReportActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.profile_report_title, handle)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(AlohaIcons.Close, stringResource(R.string.profile_cancel)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            if (state.sent) {
                Afterwards(handle, state, actions, onDone)
            } else {
                Form(remote, state, actions)
            }
        }
    }
}

@Composable
private fun Form(remote: Boolean, state: ReportUiState, actions: ReportActions) {
    Heading(R.string.profile_report_why)
    Column(Modifier.selectableGroup()) {
        ReportCategory.entries.forEach { category ->
            Choice(label(category), state.category == category, Role.RadioButton) { actions.onCategory(category) }
        }
    }
    if (state.category == ReportCategory.Violation) {
        Heading(R.string.profile_report_rules)
        state.rules.forEach { rule ->
            Choice(rule.text, rule.id in state.broken, Role.Checkbox) {
                actions.onRule(rule.id, rule.id !in state.broken)
            }
        }
    }
    OutlinedTextField(
        value = state.comment,
        onValueChange = { actions.onComment(it) },
        label = { Text(stringResource(R.string.profile_report_comment)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    if (remote) {
        Choice(stringResource(R.string.profile_report_forward), state.forward, Role.Checkbox) {
            actions.onForward(!state.forward)
        }
    }
    if (state.failed) {
        Text(
            stringResource(R.string.profile_report_failed),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Button(onClick = { actions.onSend() }, enabled = state.canSend, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.profile_report_send))
    }
}

/** Sent: what changes for the reader is theirs to choose, muting or blocking. */
@Composable
private fun Afterwards(handle: String, state: ReportUiState, actions: ReportActions, onDone: () -> Unit) {
    Text(
        stringResource(R.string.profile_report_sent),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
    Text(stringResource(R.string.profile_report_sent_body, handle))
    OutlinedButton(onClick = { actions.onMute() }, enabled = !state.muted && !state.blocked) {
        Text(stringResource(if (state.muted) R.string.profile_report_muted else R.string.profile_mute))
    }
    OutlinedButton(onClick = { actions.onBlock() }, enabled = !state.blocked) {
        Text(stringResource(if (state.blocked) R.string.profile_report_blocked else R.string.profile_block))
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_done)) }
}

@Composable
private fun Heading(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = AlohaSpacing.s).semantics { heading() },
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, role: Role, onClick: () -> Unit) {
    val row = Modifier.fillMaxWidth()
    Row(
        if (role == Role.RadioButton) {
            row.selectable(selected, role = role, onClick = onClick)
        } else {
            row.toggleable(selected, role = role) { onClick() }
        }.padding(vertical = AlohaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (role == Role.RadioButton) {
            RadioButton(selected = selected, onClick = null)
        } else {
            Checkbox(checked = selected, onCheckedChange = null)
        }
        Text(text, Modifier.padding(start = AlohaSpacing.s))
    }
}

@Composable
private fun label(category: ReportCategory): String = stringResource(
    when (category) {
        ReportCategory.Spam -> R.string.profile_report_spam
        ReportCategory.Legal -> R.string.profile_report_legal
        ReportCategory.Violation -> R.string.profile_report_violation
        ReportCategory.Other -> R.string.profile_report_other
    },
)
