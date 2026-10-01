// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import social.aloha.core.model.VideoSource

/** Serves every short's file as a few bytes, remembering which were asked for. */
private class Clips : Dispatcher() {
    val asked = CopyOnWriteArraySet<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        asked += request.url.encodedPath.removePrefix("/").removeSuffix(".mp4")
        return MockResponse.Builder().code(
            200,
        ).body(Buffer().write(ByteArray(BYTES))).addHeader("content-type", "video/mp4")
            .build()
    }

    companion object {
        const val BYTES = 1024
    }
}

@RunWith(RobolectricTestRunner::class)
class ShortsPlaybackTest {
    private val clips = Clips()
    private val server = MockWebServer().apply {
        dispatcher = clips
        start()
    }
    private val playback = ShortsPlayback(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun close() {
        playback.release()
        server.close()
    }

    @Test
    fun `the next three and the one before are loaded ahead, or only the next where the phone saves`() {
        assertEquals(listOf(-1, 1, 2, 3), (-3..5).filter { ShortsPreload.wanted(it, ShortsPreload.DEPTH) })
        assertEquals(listOf(-1, 1), (-3..5).filter { ShortsPreload.wanted(it, ShortsPreload.LEAN_DEPTH) })
        assertFalse(ShortsPreload.wanted(0, ShortsPreload.DEPTH))
    }

    // the player and the preloading run on the looper the main looper's clock drives
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(STEP_MILLIS, TimeUnit.MILLISECONDS)
            Thread.sleep(STEP_MILLIS / 2)
        }
    }

    @Test
    fun `with ten shorts and the fifth on screen, one player plays it and only its neighbours are asked for`() {
        val shorts = (0 until 10).map {
            ShortSource("s$it", listOf(VideoSource(server.url("/s$it.mp4").toString(), hls = false)))
        }
        playback.setItems(shorts)
        playback.show(4, muted = true)
        waitFor { clips.asked.containsAll(listOf("s3", "s4", "s5", "s6", "s7")) }
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertEquals(setOf("s3", "s4", "s5", "s6", "s7"), clips.asked.toSet())
        assertEquals(0f, playback.player.volume)
    }

    private companion object {
        const val STEP_MILLIS = 100L
    }
}
