// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import social.aloha.core.data.AccountRepository
import social.aloha.tv.account.AccountRoute
import social.aloha.tv.signin.TvSignInRoute

/** The TV app's one activity; every screen is a destination inside it. */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                Surface(Modifier.fillMaxSize()) { TvApp() }
            }
        }
    }
}

@Serializable
internal data object AccountKey : NavKey

@HiltViewModel
internal class TvAppViewModel @Inject constructor(accounts: AccountRepository) : ViewModel() {
    /** Whether an account is signed in; nothing until the accounts were read, so sign-in never flashes up first. */
    val signedIn: Flow<Boolean> = accounts.activeOnceRead.map { it != null }
}

/** Sign-in until there is an account; then the app, which a lost account sends back to sign-in. */
@Composable
private fun TvApp(viewModel: TvAppViewModel = hiltViewModel()) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle(initialValue = null)
    when (signedIn) {
        null -> return
        false -> return TvSignInRoute()
        true -> Unit
    }
    val backStack = rememberNavBackStack(AccountKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<AccountKey> { AccountRoute() }
        },
    )
}
