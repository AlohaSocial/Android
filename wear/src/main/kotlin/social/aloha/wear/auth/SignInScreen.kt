// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import android.app.Activity
import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import social.aloha.wear.R

private const val SERVER_KEY = "server"

@Composable
fun SignInRoute(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val askServer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val typed = result.data?.let(RemoteInput::getResultsFromIntent)?.getCharSequence(SERVER_KEY)
        typed?.let { viewModel.onServer(it.toString()) }
    }
    val label = stringResource(R.string.wear_server_label)
    SignInScreen(
        state,
        onEnterServer = {
            val input = RemoteInput.Builder(SERVER_KEY).setLabel(label)
                .wearableExtender {
                    setEmojisAllowed(false)
                    setInputActionType(EditorInfo.IME_ACTION_GO)
                }
                .build()
            askServer.launch(
                RemoteInputIntentHelper.createActionRemoteInputIntent().also {
                    RemoteInputIntentHelper.putRemoteInputsExtra(it, listOf(input))
                },
            )
        },
        onUsePhone = viewModel::onUsePhone,
        onSignOut = viewModel::onSignOut,
    )
}

@Composable
internal fun SignInScreen(
    state: SignInUiState,
    onEnterServer: () -> Unit,
    onUsePhone: () -> Unit,
    onSignOut: () -> Unit,
) {
    val list = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = list) { padding ->
        TransformingLazyColumn(state = list, contentPadding = padding) {
            item {
                ListHeader(Modifier.semantics { heading() }) { Text(stringResource(R.string.wear_app_name)) }
            }
            when (state) {
                SignInUiState.Loading -> item { CircularProgressIndicator() }

                is SignInUiState.SignedOut -> {
                    item { Centred(stringResource(state.problem?.text() ?: R.string.wear_sign_in_body)) }
                    if (state.phoneInReach) {
                        item { WideButton(spec, stringResource(R.string.wear_use_phone), onUsePhone) }
                    }
                    item { WideButton(spec, stringResource(R.string.wear_server_button), onEnterServer) }
                }

                is SignInUiState.Waiting -> {
                    item { CircularProgressIndicator() }
                    item {
                        Centred(
                            state.server?.let { stringResource(R.string.wear_waiting, it) }
                                ?: stringResource(R.string.wear_asking_phone),
                        )
                    }
                }

                is SignInUiState.SignedIn -> {
                    item { Centred(stringResource(R.string.wear_signed_in_as, state.handle)) }
                    item { WideButton(spec, stringResource(R.string.wear_sign_out), onSignOut) }
                }
            }
        }
    }
}

/** A full-width button that shrinks and fades at the round screen's edge as the list scrolls. */
@Composable
private fun TransformingLazyColumnItemScope.WideButton(spec: TransformationSpec, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(label)
    }
}

@Composable
private fun Centred(text: String) {
    // kept off the round screen's rim, where a line's ends would meet the edge and the scroll indicator
    Text(text, Modifier.fillMaxWidth().padding(horizontal = TEXT_INSET), textAlign = TextAlign.Center)
}

private val TEXT_INSET = 14.dp

private fun WatchSignInProblem.text(): Int = when (this) {
    WatchSignInProblem.Address -> R.string.wear_problem_address
    WatchSignInProblem.Unreachable -> R.string.wear_problem_unreachable
    WatchSignInProblem.Server -> R.string.wear_problem_server
    WatchSignInProblem.Phone -> R.string.wear_problem_phone
    WatchSignInProblem.Refused -> R.string.wear_problem_refused
    WatchSignInProblem.NothingOnPhone -> R.string.wear_problem_nothing_on_phone
}
