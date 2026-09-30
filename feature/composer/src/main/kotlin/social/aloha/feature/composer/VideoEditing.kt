// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** A video's length and frame, read from the file. */
@Immutable
internal data class VideoInfo(val durationMs: Long, val width: Int, val height: Int) {
    /** The shorter side, which "1080p" and "720p" name. */
    val shortSide: Int get() = minOf(width, height)
}

/** What to do to a video before it is uploaded: the part to keep, and the shorter side to scale to. */
@Immutable
internal data class VideoEdit(val startMs: Long, val endMs: Long, val shortSide: Int? = null) {
    /**
     * Roughly how large the result is, from the source's size: the share of its length kept, and the
     * share of its pixels when scaled. Good enough to say whether it will fit, which is all it is for.
     */
    fun estimate(sourceBytes: Long, info: VideoInfo): Long {
        if (info.durationMs <= 0) return sourceBytes
        val kept = (endMs - startMs).coerceAtLeast(0).toDouble() / info.durationMs
        val scale = shortSide?.takeIf { it < info.shortSide }?.let { it.toDouble() / info.shortSide } ?: 1.0
        return (sourceBytes * kept * scale * scale).toLong()
    }

    companion object {
        /** The sizes offered, each only when smaller than the video already is. */
        val SIZES = listOf(1080, 720)

        fun whole(info: VideoInfo) = VideoEdit(0, info.durationMs)
    }
}

/**
 * Trims, scales and converts a video with Media3's Transformer, into an MP4 of H.264 and AAC, which
 * every server here takes. Never throws: a video that cannot be edited stays as it was, and the
 * writer is told, rather than the attachment being lost.
 */
@OptIn(UnstableApi::class)
internal class VideoTransformer(private val context: Context) {
    fun info(file: File): VideoInfo? = runCatching {
        // closeable only from Android 10, so released by hand
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            // a phone held upright records sideways and says so: the frame as seen swaps the sides
            if (rotation % HALF_TURN ==
                QUARTER_TURN
            ) {
                VideoInfo(duration, height, width)
            } else {
                VideoInfo(duration, width, height)
            }
        } finally {
            retriever.release()
        }
    }.getOrNull()

    /** [input] with [edit] applied, written to [output]; false when Transformer could not do it. */
    suspend fun export(input: File, edit: VideoEdit?, output: File): Boolean = withContext(Dispatchers.Main) {
        val clipping = edit?.let {
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(it.startMs)
                .setEndPositionMs(it.endMs)
                .build()
        }
        val item = MediaItem.Builder().setUri(input.toUri()).apply { clipping?.let(::setClippingConfiguration) }.build()
        val effects = edit?.shortSide?.let { Effects(emptyList(), listOf(Presentation.createForShortSide(it))) }
        val edited = EditedMediaItem.Builder(item).apply { effects?.let(::setEffects) }.build()
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            continuation.resume(true)
                        }

                        override fun onError(composition: Composition, result: ExportResult, error: ExportException) {
                            output.delete()
                            continuation.resume(false)
                        }
                    },
                )
                .build()
            continuation.invokeOnCancellation {
                transformer.cancel()
                output.delete()
            }
            transformer.start(edited, output.absolutePath)
        }
    }

    private companion object {
        const val HALF_TURN = 180
        const val QUARTER_TURN = 90
    }
}
