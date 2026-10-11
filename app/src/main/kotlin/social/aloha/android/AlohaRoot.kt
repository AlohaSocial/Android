// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import social.aloha.core.designsystem.AlohaPreviews
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.Visibility
import social.aloha.core.ui.AccountChoice
import social.aloha.core.ui.AccountPicker
import social.aloha.core.ui.ActAs
import social.aloha.core.ui.LocalActAs
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.LocalStatusTranslations
import social.aloha.core.ui.PostAct
import social.aloha.feature.signin.SignInEntry

/** Sign-in until an account exists, the shell afterwards. */
@Composable
fun AlohaRoot(viewModel: AppViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (val current = session) {
        AppSession.Loading -> Unit

        is AppSession.SigningIn -> SignInEntry(
            onCancel = if (current.adding) viewModel::cancelAdding else null,
            welcome = current.first,
        )

        // a switch starts the shell afresh for that account: its own back stack, painted from its cache
        is AppSession.SignedIn -> key(current.accountId) {
            Column {
                if (current.needsReauth) ReauthBanner(current.handle, onSignInAgain = viewModel::signInAgain)
                val pending by viewModel.pendingLink.collectAsStateWithLifecycle()
                val destination by viewModel.pendingDestination.collectAsStateWithLifecycle()
                val unread by viewModel.unreadNotifications.collectAsStateWithLifecycle()
                val modes by viewModel.modes.collectAsStateWithLifecycle()
                val sharing by viewModel.pendingShare.collectAsStateWithLifecycle()
                val switcher by viewModel.switcher.collectAsStateWithLifecycle()
                val translations: TranslationsViewModel = hiltViewModel(key = "translations-${current.accountId}")
                val media: MediaPolicyViewModel = hiltViewModel()
                val policy by media.policy.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalStatusTranslations provides translations,
                    LocalSensitiveMediaPolicy provides policy,
                    LocalActAs provides rememberActAs(viewModel.switcher),
                ) {
                    AlohaApp(
                        readerId = current.accountId,
                        serverAccountId = current.serverAccountId,
                        nextcloudSocial = switcher.firstOrNull { it.id == current.accountId }?.nextcloudSocial == true,
                        pendingLink = pending,
                        onPendingLinkTaken = viewModel::externalHandled,
                        // only the shell of the account it belongs to takes it, not the one switched away from
                        pendingDestination = destination?.takeIf { it.first == current.accountId }?.second,
                        onPendingDestinationTaken = viewModel::destinationHandled,
                        unreadNotifications = unread,
                        resolveLink = viewModel::destination,
                        accountButton = { links -> AccountSwitcher(viewModel, links) },
                        modes = modes,
                    )
                    sharing?.let { content ->
                        AccountPicker(
                            stringResource(R.string.accounts_choose),
                            switcher.map { AccountChoice(it.id, it.displayName, it.handle, it.avatarUrl, it.unread) },
                            onPick = { viewModel.share(content, it.id) },
                            onDismiss = { viewModel.share(null) },
                        )
                    }
                }
            }
        }
    }
}

/** The server refused the token: nothing is lost, and one tap starts a new sign-in. */
@Composable
internal fun ReauthBanner(handle: String, onSignInAgain: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.reauth_banner, handle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSignInAgain) { Text(stringResource(R.string.reauth_sign_in_again)) }
        }
    }
}

@AlohaPreviews
@Composable
private fun ReauthBannerPreview() {
    AlohaTheme { ReauthBanner("alice", onSignInAgain = {}) }
}

/**
 * Acting as one of the reader's accounts, for every post the shell shows, and a word once each act is done.
 * One object for the shell's life, its accounts read as they change, so a new unread count recomposes only
 * what reads the accounts, not every post.
 */
@Composable
private fun rememberActAs(
    switcher: StateFlow<List<SwitcherAccount>>,
    acting: ActingViewModel = hiltViewModel(),
): ActAs {
    val all = switcher.collectAsStateWithLifecycle()
    val visibility = acting.defaultVisibility.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(acting) {
        acting.done.collect { acted ->
            if (acted.went) {
                Toast.makeText(context, resources.getString(R.string.acted, acted.handle), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.act_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    return remember(acting, all, visibility) {
        val choices = derivedStateOf {
            all.value.map { AccountChoice(it.id, it.displayName, it.handle, it.avatarUrl, it.unread) }
        }
        object : ActAs {
            override val accounts: List<AccountChoice> get() = choices.value
            override val current: String? get() = all.value.firstOrNull { it.active }?.id
            override val defaultVisibility: Visibility get() = visibility.value

            override fun act(accountId: String, url: String, act: PostAct) = acting.act(accountId, url, act)
        }
    }
}
