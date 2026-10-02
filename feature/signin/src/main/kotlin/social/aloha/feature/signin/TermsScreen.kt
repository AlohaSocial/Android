// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.designsystem.AlohaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TermsScreen(onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.terms_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(AlohaSpacing.l),
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.l),
            ) {
                Text(stringResource(R.string.terms_body), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.terms_conduct), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.terms_tools), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.terms_accept))
                }
                // declining leaves the app: nothing in it works without the terms
                OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.terms_decline))
                }
            }
        }
    }
}
