// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * What Nextcloud Social knows about a video beyond the attachment: the PeerTube-shaped facts it
 * carries on a status as `video`.
 *
 * @property support the author's "support me" text, where they wrote one.
 * @property download whether the author allows downloading; null when the server did not say.
 * @property title what the video is called, which a post has no place for but a watch page heads with.
 * @property duration seconds, where the video said.
 * @property captions the subtitles the video names, by language; addresses on the video's own server.
 */
@Serializable
public data class VideoDetails(
    val views: Int = 0,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val category: String? = null,
    val language: String? = null,
    val licence: String? = null,
    val live: Boolean = false,
    val support: String? = null,
    val download: Boolean? = null,
    val chapters: List<VideoChapter> = emptyList(),
    val title: String? = null,
    val duration: Double? = null,
    val captions: List<VideoCaption> = emptyList(),
)

/** One subtitle file of a video: WebVTT at [url], in [language] (a BCP 47 tag, as PeerTube names them). */
@Serializable
public data class VideoCaption(val language: String, val url: String)

/** One chapter of a video; [start] in seconds from the beginning. */
@Serializable
public data class VideoChapter(val start: Double, val title: String)

/**
 * Chapters written into a description the way people write them: a clock at the start of a line,
 * then the title (`0:00 Intro`, `01:02:03 - Much later`).
 */
public object VideoChapters {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val MINIMUM_CHAPTERS = 2
    private val separators = setOf('-', '–', '—', ':', '•')
    private val clockPattern = Regex("""(\d{1,4}):(\d{1,2})(?::(\d{1,2}))?""")

    /**
     * The chapters in a plain-text description in order of appearance, or an empty list when there
     * are fewer than two: one timestamp is a reference, not a table of contents. A timestamp that
     * runs backwards is a mention and is skipped.
     */
    public fun parse(plainText: String): List<VideoChapter> {
        val ordered = mutableListOf<VideoChapter>()
        for (chapter in plainText.lines().mapNotNull(::chapterOrNull)) {
            val last = ordered.lastOrNull()?.start
            if (last == null || chapter.start > last) ordered += chapter
        }
        return if (ordered.size >= MINIMUM_CHAPTERS) ordered else emptyList()
    }

    private fun chapterOrNull(rawLine: String): VideoChapter? {
        val line = rawLine.trim()
        val space = line.indexOfFirst { it == ' ' || it == '\t' }
        val start = if (space > 0) seconds(line.substring(0, space)) else null
        val title = if (space > 0) line.substring(space).trimStart { it in separators || it.isWhitespace() } else ""
        return if (start != null && title.isNotEmpty()) VideoChapter(start, title) else null
    }

    /** `m:ss`, `mm:ss` or `h:mm:ss` in seconds; anything else is null. Only the leading unit may exceed 59. */
    public fun seconds(clock: String): Double? {
        val units = clockPattern.matchEntire(clock)?.groupValues?.drop(1)?.filter {
            it.isNotEmpty()
        }?.map { it.toInt() }
        val valid = units != null && units.drop(1).all { it < SECONDS_PER_MINUTE }
        return if (valid) units.fold(0) { total, unit -> total * SECONDS_PER_MINUTE + unit }.toDouble() else null
    }

    /** Seconds as a chapter list writes them: `1:02`, `1:02:03`. */
    public fun clock(seconds: Double): String {
        val total = maxOf(0, Math.round(seconds).toInt())
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val remainder = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, remainder)
        }
    }
}
