// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.openInBrowser

/**
 * Connecting the active account to the Nextcloud its Social server runs on. The approval happens in the
 * browser; the section waits for it and shows how it went.
 */
internal object NextcloudSection : SettingsSection {
    override val key: String = "nextcloud"
    override val order: Int = 900
    override val title: Int = R.string.settings_nextcloud
    override val icon: ImageVector = AlohaIcons.Nextcloud

    @Composable
    override fun Content() {
        val viewModel: NextcloudViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(viewModel) { viewModel.loginPages.collect { openInBrowser(context, it) } }
        NextcloudRows(state, viewModel::onConnect, viewModel::onCancel, viewModel::onDisconnect)
    }
}

@Composable
internal fun NextcloudRows(
    state: NextcloudUiState,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = AlohaSpacing.l, vertical = AlohaSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
    ) {
        // one status line whose words change, so the change is spoken when the person comes back from the browser
        val status = when {
            !state.available -> R.string.settings_nextcloud_not_available
            state.connected -> R.string.settings_nextcloud_connected
            state.phase == NextcloudPhase.Waiting -> R.string.settings_nextcloud_waiting
            else -> R.string.settings_nextcloud_about
        }
        Text(stringResource(status), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        when {
            !state.available -> Unit

            state.connected -> {
                Text(stringResource(state.push.message), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.settings_nextcloud_disconnect)) }
            }

            state.phase == NextcloudPhase.Waiting ->
                TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_nextcloud_cancel)) }

            else -> {
                state.phase.message?.let {
                    Text(
                        stringResource(it),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = onConnect) { Text(stringResource(R.string.settings_nextcloud_connect)) }
            }
        }
    }
}
