// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import social.aloha.wear.auth.SignInRoute

/** The watch app's one activity; every screen is a destination inside it. */
@AndroidEntryPoint
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScaffold { SignInRoute() }
            }
        }
    }
}
