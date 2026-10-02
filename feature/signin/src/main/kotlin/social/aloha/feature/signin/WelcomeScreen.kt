// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing

/**
 * The first screen after the terms, before the first account: what the app is, what the fediverse is,
 * and Nextcloud Social first among the servers it reaches. Someone adding a further account skips it.
 */
@Composable
internal fun WelcomeScreen(onAddAccount: () -> Unit, onFindServer: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.welcome_title)
    Surface(modifier = modifier.fillMaxSize().semantics { paneTitle = title }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(AlohaSpacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.l),
            ) {
                Icon(
                    AlohaIcons.VisibilityPublic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ICON),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                    Text(
                        stringResource(R.string.welcome_fediverse_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.welcome_fediverse_body), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.welcome_nextcloud), style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = onAddAccount, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.welcome_add_account))
                }
                TextButton(onClick = onFindServer) { Text(stringResource(R.string.welcome_find_server)) }
                Text(
                    stringResource(R.string.welcome_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val ICON = 56.dp
