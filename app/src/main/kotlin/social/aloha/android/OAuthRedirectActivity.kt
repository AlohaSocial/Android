// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import social.aloha.core.data.OAuthCallbackInbox

/**
 * Receives the OAuth callback and nothing else. The Custom Tab lives in the browser's task, so the
 * callback lands here, outside the app's task: the URI goes straight to the in-process inbox, the
 * main activity is brought forward without it, and this activity finishes without drawing.
 */
@AndroidEntryPoint
class OAuthRedirectActivity : ComponentActivity() {
    @Inject
    lateinit var inbox: OAuthCallbackInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { inbox.deliver(it.toString()) }
        startActivity(
            Intent(
                this,
                MainActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
