// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import android.app.Activity
import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import social.aloha.wear.Centred
import social.aloha.wear.Heading
import social.aloha.wear.R
import social.aloha.wear.WideButton

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
            item { Heading(spec, stringResource(R.string.wear_app_name)) }
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

private fun WatchSignInProblem.text(): Int = when (this) {
    WatchSignInProblem.Address -> R.string.wear_problem_address
    WatchSignInProblem.Unreachable -> R.string.wear_problem_unreachable
    WatchSignInProblem.Server -> R.string.wear_problem_server
    WatchSignInProblem.Phone -> R.string.wear_problem_phone
    WatchSignInProblem.Refused -> R.string.wear_problem_refused
    WatchSignInProblem.NothingOnPhone -> R.string.wear_problem_nothing_on_phone
}
