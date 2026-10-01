// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import androidx.media3.common.Player
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import social.aloha.core.media.PlayerSessionService

/** The sound's media session, so it plays on in the background, next and previous going through the queue. */
@AndroidEntryPoint
public class AudioPlaybackService : PlayerSessionService() {
    @Inject internal lateinit var playback: AudioPlayback

    override val player: Player get() = playback.player

    // the video's session has the default id
    override val sessionId: String = "audio"
}
