// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import social.aloha.core.media.Sound
import social.aloha.core.media.forSound
import social.aloha.core.model.VideoSource

/**
 * Which shorts are worth loading ahead, by their distance from the one on screen: the next [depth] and
 * the one before, so a swipe either way starts at once. Everything else is its poster until it nears.
 */
internal object ShortsPreload {
    /** How far ahead, normally; one, under battery saver or Data Saver, which still autoplays the visible short. */
    const val DEPTH = 3
    const val LEAN_DEPTH = 1

    /** The first seconds of each, which is all a swipe needs before the player catches up. */
    const val LOADED_MILLIS = 2_000L

    fun wanted(distance: Int, depth: Int): Boolean = distance in 1..depth || distance == -1
}

/** One short to play: where from, best first, and its key in the pager. */
internal data class ShortSource(val id: String, val sources: List<VideoSource>)

/**
 * The shorts' one player, and what is loaded ahead of it. Only the short on screen plays; the next three
 * and the previous one are kept loaded, their first seconds only, so a swipe starts the next at once
 * without a second player (at most three live ones is the budget; this needs one). Every short loops,
 * and a source that fails gives way to the next of that short's.
 *
 * ponytail: the page being swiped in shows its poster frame, not a decoded frame; a pool of up to three
 * players, one per visible page, is the upgrade where the swipe should show moving video.
 */
@OptIn(UnstableApi::class)
internal class ShortsPlayback(private val context: Context) {
    private var current = C.INDEX_UNSET
    private var items: List<ShortSource> = emptyList()
    private val mediaItems = HashMap<String, MediaItem>()
    private var rung = 0

    private val builder = DefaultPreloadManager.Builder(
        context,
        TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> { index ->
            if (current != C.INDEX_UNSET && ShortsPreload.wanted(index - current, depth())) {
                DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(ShortsPreload.LOADED_MILLIS)
            } else {
                DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            }
        },
    )

    val player: ExoPlayer = builder.buildExoPlayer(ExoPlayer.Builder(context).forSound(Sound.Film)).apply {
        repeatMode = Player.REPEAT_MODE_ONE
        addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) = fallBack()
            },
        )
    }

    private val preload: DefaultPreloadManager = builder.build()

    /** The shorts the pager holds, in its order; new ones are loaded ahead as they come near. */
    fun setItems(shorts: List<ShortSource>) {
        // the ranking is each short's place in the pager: newer ones above move them all, so start over
        val appended = shorts.take(items.size).map { it.id } == items.map { it.id }
        if (!appended) {
            preload.reset()
            mediaItems.clear()
        }
        val known = if (appended) items.map { it.id }.toSet() else emptySet()
        items = shorts
        shorts.forEachIndexed { index, short ->
            if (short.id in known) return@forEachIndexed
            val first = short.sources.firstOrNull() ?: return@forEachIndexed
            val item = mediaItem(short.id, first)
            mediaItems[short.id] = item
            preload.add(item, index)
        }
        preload.invalidate()
    }

    /** Plays short [index], from what was loaded ahead where it was. */
    fun show(index: Int, muted: Boolean) {
        val short = items.getOrNull(index) ?: return
        val item = mediaItems[short.id] ?: return
        if (current == index && player.currentMediaItem?.mediaId == short.id) return
        current = index
        rung = 0
        preload.setCurrentPlayingIndex(index)
        preload.invalidate()
        val loaded = preload.getMediaSource(item)
        if (loaded != null) player.setMediaSource(loaded) else player.setMediaItem(item)
        player.volume = if (muted) 0f else 1f
        player.prepare()
        player.play()
    }

    /** A short starts over when it ends, or stops there. */
    fun setLoop(loop: Boolean) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    fun release() {
        preload.release()
        player.release()
    }

    private fun fallBack() {
        val short = items.getOrNull(current) ?: return
        val next = short.sources.getOrNull(rung + 1) ?: return
        rung++
        player.setMediaItem(mediaItem(short.id, next))
        player.prepare()
        player.play()
    }

    private fun mediaItem(id: String, source: VideoSource): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(source.url)
        .apply { if (source.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()

    /** Three ahead, or one where the person asked the phone to spare its battery or its data. */
    private fun depth(): Int {
        val powerSave = context.getSystemService<PowerManager>()?.isPowerSaveMode == true
        val dataSaver = context.getSystemService<ConnectivityManager>()?.restrictBackgroundStatus ==
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        return if (powerSave || dataSaver) ShortsPreload.LEAN_DEPTH else ShortsPreload.DEPTH
    }
}
