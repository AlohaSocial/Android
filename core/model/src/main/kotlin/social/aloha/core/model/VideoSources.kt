// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.net.URI

/** One place a video can be played from: an HLS playlist, or the file itself. */
public data class VideoSource(val url: String, val hls: Boolean)

/**
 * Where a video plays from, best first. The server's own transcoding ladder (`hls_url`) adapts to the
 * connection; a federated PeerTube video plays through the server's playlist, which proxies every
 * segment back through it; the plain file comes last, and is where every failure ends up. The video's
 * origin is never among them: it would tell a server the reader did not choose that they are watching,
 * and Nextcloud's content policy forbids it besides.
 */
public object VideoSources {
    public fun ladder(status: Status, attachment: MediaAttachment, apiBase: String): List<VideoSource> {
        val shown = status.displayed
        return buildList {
            attachment.hlsUrl?.takeIf { it.isNotBlank() }?.let { add(VideoSource(it, hls = true)) }
            if (shown.local == false && shown.video != null) {
                // the playlist is the attachment's, by the id the server gave its document, not the post's
                add(VideoSource(apiBase.trimEnd('/') + "/media/playlist/" + attachment.id, hls = true))
            }
            attachment.url?.takeIf { it.isNotBlank() }?.let { add(VideoSource(it, hls = false)) }
        }.distinctBy { it.url }
    }

    /**
     * The subtitles a video names that the reader's own server serves. A federated video's captions are
     * addresses on its origin, which the server does not proxy, so they are left out rather than fetched.
     */
    public fun captions(status: Status, apiBase: String): List<VideoCaption> {
        val home = hostOf(apiBase) ?: return emptyList()
        return status.displayed.video?.captions.orEmpty().filter { hostOf(it.url) == home }
    }

    private fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.net.URISyntaxException) {
        null
    }
}
