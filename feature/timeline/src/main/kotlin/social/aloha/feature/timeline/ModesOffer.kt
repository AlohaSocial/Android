// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.FeedMode

/**
 * The optional modes, offered once on Home after the first account, as the Modes settings show them.
 * Closed either way, it is not offered again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ModesOffer() {
    val viewModel: ModesSettingsViewModel = hiltViewModel()
    val offered by viewModel.offered.collectAsStateWithLifecycle()
    if (offered != false) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = viewModel::onOffered, sheetState = sheet) {
        ModesOfferContent(state, viewModel::onTurned, viewModel::onSlot) {
            scope.launch { sheet.hide() }.invokeOnCompletion { viewModel.onOffered() }
        }
    }
}

@Composable
internal fun ModesOfferContent(
    state: ModesSettingsState,
    onTurned: (FeedMode, Boolean) -> Unit,
    onSlot: (FeedMode, Int) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(bottom = AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        Text(
            stringResource(R.string.modes_offer_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m).semantics { heading() },
        )
        Text(
            stringResource(R.string.modes_offer_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m),
        )
        ModesSettingsContent(state, onTurned, onSlot)
        Button(onClick = onDone, modifier = Modifier.padding(horizontal = AlohaSpacing.m).fillMaxWidth()) {
            Text(stringResource(R.string.modes_offer_done))
        }
    }
}
