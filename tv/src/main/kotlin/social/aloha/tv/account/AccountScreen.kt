// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import social.aloha.tv.R
import social.aloha.tv.TvInsets
import social.aloha.tv.tap

@Composable
internal fun AccountRoute(viewModel: AccountViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    AccountScreen(account?.handle, account?.host, viewModel::onSignOut)
}

@Composable
internal fun AccountScreen(handle: String?, host: String?, onSignOut: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().padding(horizontal = TvInsets.horizontal, vertical = TvInsets.vertical),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            stringResource(R.string.tv_account),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        if (handle != null && host != null) {
            Text(stringResource(R.string.tv_signed_in_as, handle, host), style = MaterialTheme.typography.titleLarge)
        }
        Button(onClick = onSignOut, modifier = Modifier.focusRequester(focus).tap(onSignOut)) {
            Text(stringResource(R.string.tv_sign_out))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
