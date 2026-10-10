// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SignedInAccount

/** Where a page of what only the Nextcloud session reaches stands. */
internal enum class PageStatus { Loading, NeedsConnection, Failed, Ready }

/** Whether [account] can ask at all: a page says so before asking rather than after a refusal. */
internal fun pageStatusOf(account: SignedInAccount?): PageStatus? = when {
    account == null -> PageStatus.Failed
    !account.nextcloudConnected -> PageStatus.NeedsConnection
    else -> null
}

/**
 * The frame of a page Nextcloud Social shows only to the Nextcloud session: its title and back, and in
 * place of [content] the wait, the call to connect the Nextcloud first, or a failure with a retry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NextcloudPage(
    title: String,
    status: PageStatus,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.settings_back)) }
                },
                actions = { if (status == PageStatus.Ready) actions() },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        when (status) {
            PageStatus.Ready -> content(padding)

            PageStatus.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            PageStatus.NeedsConnection -> Notice(
                padding,
                stringResource(R.string.nextcloud_page_needs_connection),
            ) { Button(onClick = onConnect) { Text(stringResource(R.string.nextcloud_page_connect)) } }

            PageStatus.Failed -> Notice(padding, stringResource(R.string.nextcloud_page_failed)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.nextcloud_page_retry)) }
            }
        }
    }
}

@Composable
private fun Notice(padding: PaddingValues, text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.padding(padding).fillMaxSize().padding(AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        action()
    }
}
