// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import social.aloha.core.data.diagnostics.Diagnostics
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing

@HiltViewModel
internal class DiagnosticsViewModel @Inject constructor(diagnostics: Diagnostics) : ViewModel() {
    private val text = MutableStateFlow<String?>(null)

    val report: StateFlow<String?> = text.asStateFlow()

    init {
        viewModelScope.launch { text.value = diagnostics.report() }
    }
}

/** The report as it will be sent, to read first; it leaves the device only through the share button. */
@Composable
internal fun DiagnosticsPage(onClose: () -> Unit) {
    val viewModel: DiagnosticsViewModel = hiltViewModel()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AboutPage(
        title = stringResource(R.string.settings_diagnostics),
        onClose = onClose,
        actions = {
            IconButton(
                enabled = report != null,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, null))
                },
            ) {
                Icon(AlohaIcons.Share, stringResource(R.string.settings_diagnostics_share))
            }
        },
    ) { modifier ->
        val shown = report
        if (shown == null) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            SelectionContainer(
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(AlohaSpacing.l),
            ) {
                Text(shown, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
