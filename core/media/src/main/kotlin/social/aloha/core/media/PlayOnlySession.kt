// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * What another app may do through one of the app's media sessions: play, pause, seek and skip what the
 * app is playing, from the lock screen, the notification or headphones, and nothing else. A session is
 * exported, so any installed app can connect; were it allowed to set media items, it could make the
 * player fetch any address it liked, on the reader's connection.
 */
@OptIn(UnstableApi::class)
public object PlayOnlySession : MediaSession.Callback {
    internal val commands: Player.Commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        .remove(Player.COMMAND_SET_MEDIA_ITEM)
        .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        .build()

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult.accept(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS, commands)
}
