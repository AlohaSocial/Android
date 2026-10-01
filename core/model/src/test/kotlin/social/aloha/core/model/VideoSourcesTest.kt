// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoSourcesTest {
    private val apiBase = "https://cloud.example/index.php/apps/social/"
    private val author = Account("1", "alice", "alice")

    private fun video(hls: String? = null) =
        MediaAttachment("m", AttachmentKind.Video, url = "https://cloud.example/media/m.mp4", hlsUrl = hls)

    private fun post(local: Boolean?, video: VideoDetails? = null, hls: String? = null) =
        Status("9", author, mediaAttachments = listOf(video(hls)), local = local, video = video)

    @Test
    fun `a local video plays its ladder first and its file after`() {
        val status = post(local = true, hls = "https://cloud.example/media/m/master.m3u8")
        assertEquals(
            listOf(
                VideoSource("https://cloud.example/media/m/master.m3u8", hls = true),
                VideoSource("https://cloud.example/media/m.mp4", hls = false),
            ),
            VideoSources.ladder(status, status.mediaAttachments.single(), apiBase),
        )
    }

    @Test
    fun `a federated PeerTube video plays through the server's playlist, never from its origin`() {
        val status = post(local = false, video = VideoDetails(views = 3))
        val ladder = VideoSources.ladder(status, status.mediaAttachments.single(), apiBase)
        // the attachment's document, never the post: the route takes a document id
        assertEquals("https://cloud.example/index.php/apps/social/media/playlist/m", ladder.first().url)
        assertEquals(listOf(true, false), ladder.map { it.hls })
        // a boost plays what it boosts, through the same playlist
        val boost = Status("10", author, reblog = status)
        assertEquals(ladder, VideoSources.ladder(boost, status.mediaAttachments.single(), apiBase))
    }

    @Test
    fun `a plain video, or one from a server that does not say where it is from, plays its file`() {
        listOf(post(local = null), post(local = false)).forEach { status ->
            assertEquals(
                listOf(VideoSource("https://cloud.example/media/m.mp4", hls = false)),
                VideoSources.ladder(status, status.mediaAttachments.single(), apiBase),
            )
        }
    }

    @Test
    fun `only captions the reader's own server serves are offered`() {
        val details = VideoDetails(
            captions = listOf(
                VideoCaption("en", "https://cloud.example/media/captions/en.vtt"),
                VideoCaption("fr", "https://peertube.example/lazy-static/video-captions/fr.vtt"),
            ),
        )
        assertEquals(
            listOf("en"),
            VideoSources.captions(post(local = false, video = details), apiBase).map {
                it.language
            },
        )
    }
}
