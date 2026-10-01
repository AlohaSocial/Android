// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import social.aloha.core.model.VideoCaption
import social.aloha.core.model.VideoSource

/**
 * Plays a video from the best of its [sources], and moves to the next one when a source fails: at the
 * start, as a ladder whose master playlist 404s, or part way through, at the position reached, so the
 * reader sees the picture carry on rather than an error. Only the last source failing is an error the
 * screen hears of. [captions] go along with every source.
 */
public class LadderPlayback(
    private val player: Player,
    private val sources: List<VideoSource>,
    private val captions: List<VideoCaption> = emptyList(),
) : Player.Listener {
    private var rung = 0

    /** The source playing now. */
    public val current: VideoSource? get() = sources.getOrNull(rung)

    /** Starts the first source, at [positionMillis]. */
    public fun start(positionMillis: Long = 0) {
        player.addListener(this)
        current?.let { load(it, positionMillis) }
    }

    public fun release() {
        player.removeListener(this)
    }

    override fun onPlayerError(error: PlaybackException) {
        // a source that cannot be fetched or read gives way to the next; a decoder failure would not
        // play any better from another copy of the same video, so that one is the screen's to show
        if (!error.isAboutTheSource() || rung >= sources.lastIndex) return
        val position = player.currentPosition.coerceAtLeast(0)
        rung++
        current?.let { load(it, position) }
    }

    private fun load(source: VideoSource, positionMillis: Long) {
        player.setMediaItem(item(source), positionMillis)
        player.prepare()
    }

    private fun item(source: VideoSource): MediaItem = MediaItem.Builder()
        .setUri(source.url)
        .apply { if (source.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .setSubtitleConfigurations(
            captions.map { caption ->
                MediaItem.SubtitleConfiguration.Builder(caption.url.toUri())
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(caption.language)
                    .setSelectionFlags(0)
                    .build()
            },
        )
        .build()

    private fun PlaybackException.isAboutTheSource(): Boolean = errorCode in IO_ERRORS || errorCode in PARSING_ERRORS

    private companion object {
        val IO_ERRORS = PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        val PARSING_ERRORS = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
            .rangeTo(PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)
    }
}

/** What a player plays, which says how it sounds and whether it takes the speakers from other apps. */
public enum class Sound {
    /** A video with its sound: it pauses whatever else plays. */
    Film,

    /** A song, a podcast: the same, as music. */
    Music,

    /** A GIF, a muted clip: it never stops another app's sound, nor the app's own. */
    Silent,
}

/**
 * A player for [sound]: it stops when headphones are unplugged, holds the network while it plays, and
 * follows the audio focus rules a media app should.
 */
public fun mediaPlayer(context: Context, sound: Sound = Sound.Film): ExoPlayer =
    ExoPlayer.Builder(context).forSound(sound).build()

/** [mediaPlayer]'s settings, for a builder that something else, such as a preload manager, builds. */
@OptIn(UnstableApi::class)
public fun ExoPlayer.Builder.forSound(sound: Sound): ExoPlayer.Builder {
    val type = if (sound == Sound.Music) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE
    val attributes = androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(type)
    return setHandleAudioBecomingNoisy(sound != Sound.Silent)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setAudioAttributes(attributes.build(), sound != Sound.Silent)
}
