// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
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
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.sync.SyncEngine
import social.aloha.feature.video.PictureInPicturePlayer
import social.aloha.feature.video.VideoPlayback
import social.aloha.feature.video.pictureInPictureParams

@AndroidEntryPoint
open class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    @Inject lateinit var sync: SyncEngine

    @Inject lateinit var playback: VideoPlayback

    override fun onCreate(savedInstanceState: Bundle?) {
        // held until the theme is read, so a dark choice never opens on a light frame
        installSplashScreen().setKeepOnScreenCondition { app.theme.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deliver(intent)
        setContent {
            val theme by app.theme.collectAsStateWithLifecycle()
            AlohaTheme(theme ?: ThemeSettings()) {
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
        started++
        sync.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        started--
        // turned or resized, the app stays in front and its polls keep going, as they do while another
        // of its windows is still in sight
        if (started == 0 && !isChangingConfigurations) sync.setForeground(false)
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

    /** What the system's keyboard shortcuts helper lists, for Meta+/ and the timeline's "?". */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val keys = listOf(
            R.string.keys_next to KeyEvent.KEYCODE_J,
            R.string.keys_previous to KeyEvent.KEYCODE_K,
            R.string.keys_open to KeyEvent.KEYCODE_O,
            R.string.keys_favourite to KeyEvent.KEYCODE_F,
            R.string.keys_favourite to KeyEvent.KEYCODE_L,
            R.string.keys_boost to KeyEvent.KEYCODE_B,
            R.string.keys_reply to KeyEvent.KEYCODE_R,
            R.string.keys_compose to KeyEvent.KEYCODE_N,
        ).map { (label, key) -> KeyboardShortcutInfo(getString(label), key, 0) }
        data += KeyboardShortcutGroup(getString(R.string.keys_timeline), keys)
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
        when (val request = OutsideRequest.of(intent, packageName)) {
            is OutsideRequest.Link -> app.openExternal(request.address, request.handedOver)
            is OutsideRequest.Share -> app.share(request.content, request.accountId)
            is OutsideRequest.Draft -> app.openDraft(request.accountId, request.draftId)
            is OutsideRequest.Open -> app.openNotification(request.accountId, request.statusId, request.profileId)
            is OutsideRequest.Compose -> app.openDraft(request.accountId, draftId = UUID.randomUUID().toString())
            is OutsideRequest.Search -> app.openSearch(request.accountId)
            null -> Unit
        }
    }

    private companion object {
        /** The app's windows in sight; only the main thread counts them. */
        var started = 0
    }
}
