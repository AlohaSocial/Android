// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaSpacing

private val ContentWidth = 560.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignInScreen(state: SignInUiState, actions: SignInActions, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.signin_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentWidth)
                    .fillMaxWidth()
                    .padding(AlohaSpacing.l),
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.l),
            ) {
                SignInStepContent(state, actions)
            }
        }
    }
}

@Composable
private fun SignInStepContent(state: SignInUiState, actions: SignInActions) {
    when (val step = state.step) {
        is SignInStep.EnterServerWith -> ServerEntry(state, step.problem, actions)
        SignInStep.Probing -> StatusMessage(stringResource(R.string.signin_probing))
        is SignInStep.Instance -> InstanceCardContent(step.card, actions)
        SignInStep.WaitingForBrowser -> WaitingForBrowser(actions)
        SignInStep.Finishing -> StatusMessage(stringResource(R.string.signin_finishing))
        is SignInStep.Failed -> FailureContent(state, step.failure, actions)
        is SignInStep.UntrustedCertificate -> CertificatePrompt(step.certificate, actions)
    }
}

@Composable
private fun ServerEntry(state: SignInUiState, problem: AddressProblem?, actions: SignInActions) {
    val invalid = problem != null
    val invalidText = when (problem) {
        AddressProblem.NotSecure -> stringResource(R.string.signin_server_not_secure)
        else -> stringResource(R.string.signin_server_invalid)
    }
    OutlinedTextField(
        value = state.server,
        onValueChange = actions::onServerChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (invalid) error(invalidText) },
        label = { Text(stringResource(R.string.signin_server_label)) },
        supportingText = {
            Text(
                if (invalid) invalidText else stringResource(R.string.signin_server_supporting),
                modifier = Modifier.semantics { if (invalid) liveRegion = LiveRegionMode.Assertive },
            )
        },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { actions.onContinue() }),
    )
    Button(onClick = actions::onContinue, enabled = state.server.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.signin_continue))
    }
    ManualApiAddress(state, actions)
}
