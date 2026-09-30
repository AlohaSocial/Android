// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.sync.PostQueue
import social.aloha.core.sync.SyncEngine

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    @Inject lateinit var sync: SyncEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deliver(intent)
        setContent {
            AlohaTheme {
                // test tags double as resource ids, so a release-build benchmark finds its lists
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { AlohaRoot(app) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sync.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        // turned or resized, the app stays in front and its polls keep going
        if (!isChangingConfigurations) sync.setForeground(false)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        sync.noteInteraction()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deliver(intent)
    }

    /**
     * A link another app handed over, or a post the outbox asks about; what either opens is decided in
     * the app, never trusted as given.
     */
    private fun deliver(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let(app::openExternal)

            PostQueue.ACTION_OPEN_DRAFT -> intent.getStringExtra(PostQueue.EXTRA_ACCOUNT)?.let { account ->
                app.openDraft(account, intent.getStringExtra(PostQueue.EXTRA_DRAFT))
            }

            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> SharedContent.from(intent, packageName)?.let(app::share)
        }
    }
}
