// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.testing.SignedInFixture

/** A media route that answers each request with the next of [answers] and remembers the paths asked. */
private class Media(private val answers: MutableList<Pair<Int, String>>) : Dispatcher() {
    val paths = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        paths += "${request.method} ${request.url.encodedPath}"
        val (code, body) = answers.removeFirstOrNull() ?: (500 to "{}")
        return MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
    }
}

@RunWith(RobolectricTestRunner::class)
class MediaUploadWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val answers = mutableListOf<Pair<Int, String>>()
    private val media = Media(answers)
    private val server = MockWebServer().apply {
        dispatcher = media
        start()
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private fun attachment(id: String, url: String?) =
        """{"id":"$id","type":"image","url":${url?.let { "\"$it\"" } ?: "null"},"preview_url":null}"""

    private suspend fun run(attempt: Int = 0): ListenableWorker.Result {
        val account = fixture.signIn(server.url("/"))
        val file = File(context.cacheDir, "beach.jpg").apply { writeBytes(ByteArray(2048)) }
        val worker = TestListenableWorkerBuilder<MediaUploadWorker>(context)
            .setInputData(
                workDataOf(
                    MediaUploadWorker.ACCOUNT to account.id,
                    MediaUploadWorker.PATH to file.absolutePath,
                    MediaUploadWorker.NAME to "beach.jpg",
                    MediaUploadWorker.MIME to "image/jpeg",
                ),
            )
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = MediaUploadWorker(
                        appContext,
                        workerParameters,
                        fixture.accounts,
                        MediaRepository(fixture.clients),
                    )
                },
            )
            .build()
        return worker.doWork()
    }

    @Test
    fun `a server without the newer route takes the upload on the older one`() = runBlocking {
        answers += listOf(404 to "{}", 200 to attachment("m1", "https://x.test/m1.jpg"))
        val result = run()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("m1", result.outputData.getString(MediaUploadWorker.MEDIA_ID))
        assertEquals(listOf("POST /api/v2/media", "POST /api/v1/media"), media.paths)
    }

    @Test
    fun `an upload the server is still converting is waited for`() = runBlocking {
        answers += listOf(202 to attachment("m2", null), 206 to attachment("m2", null), 200 to attachment("m2", "u"))
        val result = run()
        assertEquals("m2", result.outputData.getString(MediaUploadWorker.MEDIA_ID))
        assertEquals(listOf("POST /api/v2/media", "GET /api/v1/media/m2", "GET /api/v1/media/m2"), media.paths)
    }

    @Test
    fun `a refusal says why and is not tried again`() = runBlocking {
        answers += 422 to """{"error":"File type not allowed"}"""
        val result = run()
        assertTrue(result is ListenableWorker.Result.Failure)
        assertTrue(result.outputData.getBoolean(MediaUploadWorker.REFUSED, false))
        assertEquals("File type not allowed", result.outputData.getString(MediaUploadWorker.MESSAGE))
    }

    @Test
    fun `a server that fails is tried again, until the attempts run out`() = runBlocking {
        answers += 500 to "{}"
        assertTrue(run() is ListenableWorker.Result.Retry)
        answers += 500 to "{}"
        val last = run(attempt = 5)
        assertTrue(last is ListenableWorker.Result.Failure)
        assertTrue(!last.outputData.getBoolean(MediaUploadWorker.REFUSED, true))
    }

    @Test
    fun `a PHP upload limit is a refusal, not tried again`() = runBlocking {
        answers += 413 to "<html>Request Entity Too Large</html>"
        val result = run()
        assertTrue(result is ListenableWorker.Result.Failure)
        assertTrue(result.outputData.getBoolean(MediaUploadWorker.REFUSED, false))
    }
}
