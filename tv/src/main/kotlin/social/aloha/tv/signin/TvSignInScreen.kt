// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import social.aloha.core.designsystem.QrCode
import social.aloha.tv.R
import social.aloha.tv.TvInsets
import social.aloha.tv.TvTextField
import social.aloha.tv.tap

@Composable
internal fun TvSignInRoute(viewModel: TvSignInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSignInScreen(state, viewModel::onServer, viewModel::onCode, viewModel::onStartOver)
}

@Composable
internal fun TvSignInScreen(
    state: TvSignInState,
    onServer: (String) -> Unit,
    onCode: (String) -> Unit,
    onStartOver: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = TvInsets.horizontal, vertical = TvInsets.vertical),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            stringResource(R.string.tv_sign_in_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        when (state) {
            is TvSignInState.Server -> ServerStep(state.problem, onServer)

            TvSignInState.Working -> Text(
                stringResource(R.string.tv_working),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            is TvSignInState.Code -> CodeStep(state, onCode, onStartOver)
        }
    }
}

@Composable
private fun ServerStep(problem: TvSignInProblem?, onServer: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.tv_sign_in_body), style = MaterialTheme.typography.titleMedium)
        TvTextField(
            value = typed,
            onValueChange = { typed = it },
            label = stringResource(R.string.tv_server_label),
            onDone = { onServer(typed) },
            modifier = Modifier.focusRequester(focus),
        )
        problem?.let { Problem(it) }
        Button(onClick = { onServer(typed) }, modifier = Modifier.tap { onServer(typed) }) {
            Text(stringResource(R.string.tv_continue))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun CodeStep(state: TvSignInState.Code, onCode: (String) -> Unit, onStartOver: () -> Unit) {
    var typed by rememberSaveable(state.url) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val described = stringResource(R.string.tv_qr_described, state.host)
    // the code at the top right, clear of the system keyboard
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.tv_scan, state.host), style = MaterialTheme.typography.titleLarge)
            TvTextField(
                value = typed,
                onValueChange = { typed = it },
                label = stringResource(R.string.tv_code_label),
                onDone = { onCode(typed) },
                keyboardType = KeyboardType.Ascii,
                modifier = Modifier.focusRequester(focus),
            )
            state.problem?.let { Problem(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onCode(typed) }, modifier = Modifier.tap { onCode(typed) }) {
                    Text(stringResource(R.string.tv_sign_in))
                }
                OutlinedButton(onClick = onStartOver, modifier = Modifier.tap(onStartOver)) {
                    Text(stringResource(R.string.tv_other_server))
                }
            }
        }
        Box(
            Modifier.size(QR_SIZE).clip(MaterialTheme.shapes.medium).background(Color.White).padding(12.dp),
        ) {
            QrCode(state.url, Modifier.fillMaxSize().semantics { contentDescription = described })
        }
    }
    LaunchedEffect(state.url) { focus.requestFocus() }
}

@Composable
private fun Problem(problem: TvSignInProblem) {
    Text(
        stringResource(problem.text()),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun TvSignInProblem.text(): Int = when (this) {
    TvSignInProblem.Address -> R.string.tv_problem_address
    TvSignInProblem.Insecure -> R.string.tv_problem_insecure
    TvSignInProblem.Unreachable -> R.string.tv_problem_unreachable
    TvSignInProblem.NotAServer -> R.string.tv_problem_not_a_server
    TvSignInProblem.Certificate -> R.string.tv_problem_certificate
    TvSignInProblem.Server -> R.string.tv_problem_server
    TvSignInProblem.CodeRefused -> R.string.tv_problem_code
}

// above the system keyboard, which TVs open over the lower middle of the screen as soon as the code field has focus
private val QR_SIZE = 200.dp
