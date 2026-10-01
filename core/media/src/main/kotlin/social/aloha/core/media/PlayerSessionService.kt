// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * A media session over a player the app keeps for itself: what keeps it playing with the app in the
 * background, and puts its controls on the lock screen, in the notification and on headphones, for
 * other apps to play and pause through but never to load anything into ([PlayOnlySession]). A tap on
 * the notification opens the app again; swiping the app away ends what nobody is listening to.
 */
public abstract class PlayerSessionService : MediaSessionService() {
    /** The player, which outlives the service. */
    protected abstract val player: Player

    /** Two sessions in one app must differ; null is the default one. */
    protected open val sessionId: String? = null

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val builder = MediaSession.Builder(this, player).setCallback(PlayOnlySession)
        sessionId?.let(builder::setId)
        packageManager.getLaunchIntentForPackage(packageName)?.let { open ->
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, open, flags))
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
