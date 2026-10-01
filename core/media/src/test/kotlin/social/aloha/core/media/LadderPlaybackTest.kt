// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import social.aloha.core.model.Account
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Status
import social.aloha.core.model.VideoDetails
import social.aloha.core.model.VideoSources

/** A Nextcloud with no ladder and no proxied playlist for this video: only the file answers. */
private class NoLadder : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += path
        return if (path.endsWith(".mp4")) {
            // a few bytes are enough: the player has moved on to the file once it asks for it
            MockResponse.Builder().code(
                200,
            ).body(Buffer().write(ByteArray(BYTES))).addHeader("content-type", "video/mp4")
                .build()
        } else {
            MockResponse.Builder().code(404).body("").build()
        }
    }

    companion object {
        const val BYTES = 1024
    }
}

@RunWith(RobolectricTestRunner::class)
class LadderPlaybackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cloud = NoLadder()
    private val server = MockWebServer().apply {
        dispatcher = cloud
        start()
    }

    // where the video came from, which must never hear of the reader
    private val origin = MockWebServer().apply { start() }
    private val player = mediaPlayer(context)

    @After
    fun close() {
        player.release()
        server.close()
        origin.close()
    }

    // the player retries a playlist on the clock the main looper drives, which only moves when told to
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(STEP_MILLIS, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    @Test
    fun `a federated video falls from the proxied playlist to the file, and its origin hears nothing`() {
        val apiBase = server.url("/index.php/apps/social/").toString()
        val video = MediaAttachment(
            "m",
            AttachmentKind.Video,
            url = server.url("/index.php/apps/social/media/m.mp4").toString(),
            remoteUrl = origin.url("/static/web-videos/m.mp4").toString(),
            hlsUrl = server.url("/index.php/apps/social/media/m/master.m3u8").toString(),
        )
        val status =
            Status(
                "9",
                Account("1", "alice", "alice"),
                mediaAttachments = listOf(video),
                local = false,
                video = VideoDetails(),
            )
        val ladder = LadderPlayback(player, VideoSources.ladder(status, video, apiBase))
        ladder.start()
        waitFor { cloud.asked.any { it.endsWith(".mp4") } }
        assertEquals(
            listOf(
                "/index.php/apps/social/media/m/master.m3u8",
                "/index.php/apps/social/media/playlist/m",
                "/index.php/apps/social/media/m.mp4",
            ),
            cloud.asked.distinct(),
        )
        assertTrue(ladder.current?.url?.endsWith(".mp4") == true)
        assertEquals(0, origin.requestCount)
    }
}

private const val STEP_MILLIS = 250L
