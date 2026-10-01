// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import androidx.media3.common.Player
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import social.aloha.core.media.PlayerSessionService

/** The video's media session, so it plays on, heard only, with the app in the background. */
@AndroidEntryPoint
public class VideoPlaybackService : PlayerSessionService() {
    @Inject internal lateinit var playback: VideoPlayback

    override val player: Player get() = playback.player
}
