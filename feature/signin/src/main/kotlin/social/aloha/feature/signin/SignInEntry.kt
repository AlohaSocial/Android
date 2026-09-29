// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.security.KeyChain
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Where a person without an account starts: the terms, once per version, then sign-in. Signing in
 * makes an account active, and the app leaves this screen by itself.
 */
@Composable
public fun SignInEntry(modifier: Modifier = Modifier) {
    val terms: TermsViewModel = hiltViewModel()
    val accepted by terms.accepted.collectAsStateWithLifecycle()
    when (accepted) {
        null -> Unit
        false -> TermsScreen(onAccept = terms::accept, modifier = modifier)
        true -> SignInRoute(modifier)
    }
}

@Composable
private fun SignInRoute(modifier: Modifier) {
    val viewModel: SignInViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val browserUrl by viewModel.pendingBrowserUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val instructions by rememberUpdatedState(
        stringResource(R.string.signin_admin_instructions, state.server.trim(), WEB_SERVER_RULES),
    )

    LaunchedEffect(browserUrl) {
        browserUrl?.let {
            openInBrowser(context, it)
            viewModel.onBrowserOpened()
        }
    }
    val actions = remember(viewModel, context) {
        object : SignInActions by viewModel {
            override fun onCopyInstructions() {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("instructions", instructions)))
                    viewModel.onCopyInstructions()
                }
            }

            override fun onChooseClientCertificate() {
                val host = viewModel.clientCertificateHost() ?: return
                val activity = context.findActivity() ?: return
                // the callback arrives on a binder thread; the ViewModel hands the write to I/O
                KeyChain.choosePrivateKeyAlias(activity, { alias ->
                    viewModel.onClientCertificateChosen(host, alias)
                }, null, null, host, -1, null)
            }
        }
    }
    SignInScreen(state, actions, modifier)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A Custom Tab shares the browser's Nextcloud session, so single sign-on and two-factor prompts work
 * as they do on the web; without a Custom Tabs browser the default browser opens the page.
 */
private fun openInBrowser(context: Context, url: String) {
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** The web-server rules that ship with Nextcloud Social, with notes on proxies and the Authorization header. */
private const val WEB_SERVER_RULES = "https://github.com/nextcloud/social/tree/master/contrib/webserver"
