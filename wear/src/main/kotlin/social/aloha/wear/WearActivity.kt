// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import social.aloha.wear.auth.SignInRoute
import social.aloha.wear.auth.WatchAccounts
import social.aloha.wear.social.HomeRoute
import social.aloha.wear.social.NotificationsRoute
import social.aloha.wear.social.PostRoute
import social.aloha.wear.social.SocialLinks

/** The watch app's one activity; every screen is a destination inside it. */
@AndroidEntryPoint
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScaffold { WearApp() }
            }
        }
    }
}

@Serializable
private data object HomeKey : NavKey

@Serializable
private data object NotificationsKey : NavKey

@Serializable
private data class PostKey(val id: String) : NavKey

@Serializable
private data object AccountKey : NavKey

@HiltViewModel
internal class WearAppViewModel @Inject constructor(accounts: WatchAccounts) : ViewModel() {
    /** Whether the watch is signed in; nothing until the vault was read, so sign-in never flashes up first. */
    val signedIn: Flow<Boolean> = accounts.account.map { it != null }
}

/** Sign-in until there is an account; then its timeline, which a lost account sends back to sign-in. */
@Composable
private fun WearApp(viewModel: WearAppViewModel = hiltViewModel()) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle(initialValue = null)
    when (signedIn) {
        null -> return
        false -> return SignInRoute()
        true -> Unit
    }
    val backStack = rememberNavBackStack(HomeKey)
    val links = SocialLinks(
        onPost = { backStack.add(PostKey(it)) },
        onNotifications = { backStack.add(NotificationsKey) },
        onAccount = { backStack.add(AccountKey) },
    )
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> { HomeRoute(links) }
            entry<NotificationsKey> { NotificationsRoute(links.onPost) }
            entry<PostKey> { PostRoute(it.id) }
            entry<AccountKey> { SignInRoute() }
        },
    )
}
