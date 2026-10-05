// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import social.aloha.core.data.AppLockSettings
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.ReadingStyle
import social.aloha.core.sync.DeviceConditions
import social.aloha.core.sync.SyncEngine
import social.aloha.core.ui.LocalOnMobileData
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.feature.video.PictureInPicturePlayer
import social.aloha.feature.video.VideoPlayback
import social.aloha.feature.video.pictureInPictureParams

@AndroidEntryPoint
open class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    @Inject lateinit var sync: SyncEngine

    @Inject lateinit var playback: VideoPlayback

    @Inject lateinit var reading: ReadingPreferences

    @Inject lateinit var conditions: DeviceConditions

    @Inject lateinit var lock: AppLock

    @Inject lateinit var lockSettings: AppLockSettings

    // the screen lock's own confirmation, where Android 8 and 9 have no prompt that offers it
    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) lock.unlock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // held until the theme is read, so a dark choice never opens on a light frame
        // held until the theme and the lock are read, so a dark choice never opens on a light frame and
        // nothing of the app shows before it is known whether it is locked
        installSplashScreen().setKeepOnScreenCondition { app.theme.value == null || lock.locked.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deliver(intent)
        // while the lock is on, the recents screen shows no picture of the app, and nothing can capture it
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                lockSettings.enabled.collect { on ->
                    if (on) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        setContent {
            val locked by lock.locked.collectAsStateWithLifecycle()
            val opened by lock.opened.collectAsStateWithLifecycle()
            val theme by app.theme.collectAsStateWithLifecycle()
            val style by reading.style.collectAsStateWithLifecycle(ReadingStyle())
            AlohaTheme(theme ?: ThemeSettings()) {
                // ponytail: read as the window composes; a move onto mobile data counts from the next recomposition
                CompositionLocalProvider(
                    LocalReadingStyle provides style,
                    LocalOnMobileData provides conditions.metered,
                ) {
                    Window(locked, opened)
                }
            }
        }
    }

    /**
     * Until the reader got in, nothing of the app is composed: no screen, no post, no notification opened.
     * Locked again later, it stays composed beneath the lock, hidden from accessibility services as from
     * screenshots, so what was open is still open once unlocked.
     */
    @Composable
    private fun Window(locked: Boolean?, opened: Boolean) {
        Box(Modifier.fillMaxSize()) {
            if (opened) {
                // test tags double as resource ids, so a release-build benchmark finds its lists
                val hidden = if (locked != false) Modifier.clearAndSetSemantics {} else Modifier
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.then(hidden)) {
                    AlohaRoot(app)
                    // the video alone fills a picture-in-picture window; the app stays composed beneath it, so
                    // what was open is still open when the window grows back
                    val inPictureInPicture by playback.inPictureInPicture.collectAsStateWithLifecycle()
                    if (inPictureInPicture) PictureInPicturePlayer(playback)
                }
            }
            if (locked == true) LockScreen(onUnlock = ::askToUnlock)
        }
    }

    override fun onStart() {
        super.onStart()
        if (started == 0) lock.onBack()
        started++
        sync.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        started--
        // turned or resized, the app stays in front and its polls keep going, as they do while another
        // of its windows is still in sight
        if (started == 0 && !isChangingConfigurations) {
            sync.setForeground(false)
            lock.onAway()
        }
    }

    /**
     * The fingerprint, the face or the screen lock, whichever the reader has. A device whose screen lock
     * was removed has nothing to ask with, and opens.
     */
    private fun askToUnlock() {
        val keyguard = getSystemService<KeyguardManager>()
        if (keyguard?.isDeviceSecure != true) return lock.unlock()
        val title = getString(R.string.lock_prompt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            prompt(title)
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(title, null)
            if (intent != null) confirmCredential.launch(intent) else lock.unlock()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun prompt(title: String) {
        val builder = BiometricPrompt.Builder(this).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = lock.unlock()
            },
        )
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playback.inPictureInPicture.value = isInPictureInPictureMode
    }

    // from Android 12 the system enters picture-in-picture on its own; before that, leaving asks for it
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && playback.wantsPictureInPicture.value) {
            enterPictureInPictureMode(pictureInPictureParams(this, playback.player.isPlaying))
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
