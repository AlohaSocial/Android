// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.net.URI
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AuthorizedApp
import social.aloha.core.navigation.AuthorizedAppsKey
import social.aloha.core.ui.fullDate
import social.aloha.core.ui.readingColumn

/** The applications holding a key to the account, each with the way to sign it out. */
@Composable
public fun AuthorizedAppsRoute(
    key: AuthorizedAppsKey,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<AuthorizedAppsViewModel, AuthorizedAppsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AuthorizedAppsScreen(state, viewModel, onConnect, onBack, modifier)
}

@Composable
internal fun AuthorizedAppsScreen(
    state: AuthorizedAppsUiState,
    actions: AuthorizedAppsActions,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.apps_revoke_failed)
    LaunchedEffect(state.revokeFailed) {
        if (state.revokeFailed) {
            actions.onRevokeFailureShown()
            snackbars.showSnackbar(refused)
        }
    }
    var revoking by rememberSaveable { mutableStateOf<String?>(null) }
    NextcloudPage(
        stringResource(R.string.apps_title),
        state.status,
        onBack,
        onConnect,
        actions::onRetry,
        modifier,
        snackbars,
    ) { padding ->
        if (state.apps.isEmpty()) {
            Text(
                stringResource(R.string.apps_empty),
                Modifier.padding(padding).padding(AlohaSpacing.l),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize().readingColumn()) {
                items(state.apps, key = { it.id }) { app -> AppRow(app) { revoking = app.id } }
            }
        }
    }
    val app = state.apps.firstOrNull { it.id == revoking }
    if (app != null) {
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(R.string.apps_revoke_title, app.shownName())) },
            text = { Text(stringResource(R.string.apps_revoke_body)) },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    actions.onRevoke(app.id)
                }) { Text(stringResource(R.string.apps_revoke_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { revoking = null }) { Text(stringResource(R.string.apps_cancel)) }
            },
        )
    }
}

@Composable
private fun AppRow(app: AuthorizedApp, onRevoke: () -> Unit) {
    val name = app.shownName()
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            Column {
                app.website?.let { site -> Text(runCatching { URI(site).host }.getOrNull() ?: site) }
                (app.signedIn ?: app.createdAt)?.let {
                    Text(stringResource(R.string.apps_signed_in, fullDate(it)))
                }
                app.lastUsedAt?.let { Text(stringResource(R.string.apps_last_used, fullDate(it))) }
                if (app.scopes.isNotEmpty()) {
                    Text(app.scopes.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            val label = stringResource(R.string.apps_revoke_for, name)
            TextButton(onClick = onRevoke, modifier = Modifier.semantics { contentDescription = label }) {
                Text(stringResource(R.string.apps_revoke), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun AuthorizedApp.shownName(): String = name.ifBlank { stringResource(R.string.apps_unnamed) }
