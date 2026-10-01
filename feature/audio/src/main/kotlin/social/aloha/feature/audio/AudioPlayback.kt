// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.media.Sound
import social.aloha.core.media.mediaPlayer

/** One post's sound, as the queue holds it and the lock screen names it. */
public data class AudioItem(
    val statusId: String,
    val url: String,
    val title: String,
    val artist: String,
    val artwork: String?,
)

/** What plays now, and whether it is playing or paused. */
public data class NowPlaying(val item: AudioItem, val playing: Boolean)

/**
 * The one sound playing, from whichever list started it: it goes on in the background, its controls on
 * the lock screen, in the notification and on headphones, and from track to track through the queue the
 * list it started from made. The mini player above the navigation shows it wherever the reader goes.
 * A switch to another account, or signing out, stops it: its posts are that account's.
 */
@Singleton
public class AudioPlayback @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    accounts: AccountRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val state = MutableStateFlow<NowPlaying?>(null)
    public val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()

    private var queue: List<AudioItem> = emptyList()

    public val player: ExoPlayer by lazy { mediaPlayer(context, Sound.Music).also { it.addListener(listener) } }

    init {
        scope.launch(Dispatchers.Main) {
            accounts.activeAccount.map { it?.id }.distinctUntilChanged().drop(1).collect { stop() }
        }
    }

    /** Plays [items] from [index] on; the item already playing just goes on, or resumes where paused. */
    public fun play(items: List<AudioItem>, index: Int) {
        val wanted = items.getOrNull(index) ?: return
        if (state.value?.item?.statusId == wanted.statusId) {
            resume()
            return
        }
        queue = items
        player.setMediaItems(items.map(::mediaItem), index, 0)
        player.prepare()
        player.play()
        // the session, its notification and lock screen controls, for as long as there is something to play
        context.startService(Intent(context, AudioPlaybackService::class.java))
    }

    public fun toggle() {
        if (player.isPlaying) player.pause() else resume()
    }

    // after a failure the player must load again, and at the end it starts over, as the lock screen's play does
    @OptIn(UnstableApi::class)
    private fun resume() {
        Util.handlePlayButtonAction(player)
    }

    /** Stops and empties the queue: the mini player goes, and so does the notification. */
    public fun stop() {
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        state.value = null
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = update()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = update()
    }

    private fun update() {
        val id = player.currentMediaItem?.mediaId
        val item = queue.firstOrNull { it.statusId == id }
        state.value = item?.let { NowPlaying(it, player.isPlaying) }
    }

    private fun mediaItem(item: AudioItem): MediaItem = MediaItem.Builder()
        .setMediaId(item.statusId)
        .setUri(item.url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setArtworkUri(item.artwork?.toUri())
                .build(),
        )
        .build()
}
