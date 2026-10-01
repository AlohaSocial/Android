// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.navigation.AppIntents
import social.aloha.core.sync.PostQueue
import social.aloha.core.sync.SyncEngine
import social.aloha.feature.video.PictureInPicturePlayer
import social.aloha.feature.video.VideoPlayback
import social.aloha.feature.video.pictureInPictureParams

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    @Inject lateinit var sync: SyncEngine

    @Inject lateinit var playback: VideoPlayback

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deliver(intent)
        setContent {
            AlohaTheme {
                // test tags double as resource ids, so a release-build benchmark finds its lists
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    AlohaRoot(app)
                    // the video alone fills a picture-in-picture window; the app stays composed beneath it, so
                    // what was open is still open when the window grows back
                    val inPictureInPicture by playback.inPictureInPicture.collectAsStateWithLifecycle()
                    if (inPictureInPicture) PictureInPicturePlayer(playback)
                }
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playback.inPictureInPicture.value = isInPictureInPictureMode
    }

    // from Android 12 the system enters picture-in-picture on its own; before that, leaving asks for it
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && playback.wantsPictureInPicture.value) {
            enterPictureInPictureMode(pictureInPictureParams())
        }
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

            AppIntents.ACTION_OPEN -> intent.getStringExtra(AppIntents.EXTRA_ACCOUNT)?.let { account ->
                app.openNotification(
                    account,
                    intent.getStringExtra(AppIntents.EXTRA_STATUS),
                    intent.getStringExtra(AppIntents.EXTRA_PROFILE),
                )
            }

            AppIntents.ACTION_COMPOSE -> intent.getStringExtra(AppIntents.EXTRA_ACCOUNT)?.let {
                app.openDraft(it, draftId = UUID.randomUUID().toString())
            }
        }
    }
}
