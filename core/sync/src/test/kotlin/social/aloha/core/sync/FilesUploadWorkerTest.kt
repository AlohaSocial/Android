// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.network.HttpExchange
import social.aloha.core.network.RateLimiter
import social.aloha.core.nextcloud.NextcloudFiles
import social.aloha.core.testing.DavServer
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class FilesUploadWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val dav = DavServer()
    private val server = MockWebServer().apply {
        dispatcher = dav
        start()
    }
    private val files = NextcloudFiles(
        HttpExchange(OkHttpClient(), RateLimiter(nowMillis = { 0L }), Dispatchers.IO),
        fixture.vault,
        Dispatchers.IO,
    )

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            FilesUploadWorker(appContext, workerParameters, fixture.accounts, files)
    }

    init {
        // the notification's cancel action asks WorkManager for its intent, and the cancelled upload runs in it
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private fun copy() = File(context.cacheDir, "picked.png").apply { writeText("a picture") }

    private suspend fun connected() = fixture.signIn(server.url("/")).also {
        fixture.connectNextcloud(it.id, DavServer.BASIC)
    }

    private fun run(source: File, attempt: Int = 0): ListenableWorker.Result = runBlocking {
        val account = connected()
        val worker = TestListenableWorkerBuilder<FilesUploadWorker>(context)
            .setInputData(
                workDataOf(
                    FilesUploadWorker.ACCOUNT to account.id,
                    FilesUploadWorker.PATH to source.absolutePath,
                    FilesUploadWorker.NAME to "beach.png",
                    FilesUploadWorker.FOLDER to "Aloha fixtures",
                    FilesUploadWorker.UPLOAD_ID to "upload-1",
                ),
            )
            .setRunAttemptCount(attempt)
            .setWorkerFactory(factory)
            .build()
        worker.doWork()
    }

    @Test
    fun `a saved file says where it went, and the app's copy goes`() {
        dav.answer("PUT", "/remote.php/dav/files/alice/Aloha%20fixtures/beach.png", 201)
        val source = copy()
        val result = run(source)
        assertEquals(
            ListenableWorker.Result.success(workDataOf(FilesUploadWorker.SAVED to "Aloha fixtures/beach.png")),
            result,
        )
        assertFalse(source.exists())
    }

    @Test
    fun `a busy server is tried again, with the copy kept for it`() {
        dav.answer("PUT", "/remote.php/dav/files/alice/Aloha%20fixtures/beach.png", 503)
        val source = copy()
        assertEquals(ListenableWorker.Result.retry(), run(source))
        assertTrue(source.exists())
    }

    @Test
    fun `a refusal is final, and nothing of the upload is left`() {
        dav.answer("PUT", "/remote.php/dav/files/alice/Aloha%20fixtures/beach.png", 403)
        val source = copy()
        assertEquals(ListenableWorker.Result.failure(), run(source))
        assertFalse(source.exists())
        assertTrue(
            dav.asked.any {
                it.method == "DELETE" &&
                    it.url.encodedPath == "/remote.php/dav/uploads/alice/upload-1"
            },
        )
    }

    @Test
    fun `the last try of a busy server gives up`() {
        dav.answer("PUT", "/remote.php/dav/files/alice/Aloha%20fixtures/beach.png", 503)
        val source = copy()
        assertEquals(ListenableWorker.Result.failure(), run(source, attempt = 5))
        assertFalse(source.exists())
    }

    @Test
    fun `an upload cancelled while it goes out leaves nothing behind`() = runBlocking {
        val account = connected()
        val sending = CountDownLatch(1)
        val release = CountDownLatch(1)
        dav.handler = { request ->
            if (request.method == "PUT") {
                sending.countDown()
                release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                MockResponse.Builder().code(201).build()
            } else {
                null
            }
        }
        val source = copy()
        val uploads = FilesUploads(context)
        val id = uploads.enqueue(account.id, source, "beach.png", "Aloha fixtures")
        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(id)
        assertTrue(sending.await(WAIT_SECONDS, TimeUnit.SECONDS))
        uploads.cancel(id)
        withTimeout(WAIT_SECONDS * 1_000) {
            while (source.exists() || dav.asked.none { it.method == "DELETE" }) delay(POLL_MILLIS)
        }
        release.countDown()
    }

    @Test
    fun `an upload is watched in the folder it goes into, by the name it was given`() = runBlocking {
        val uploads = FilesUploads(context)
        val id = uploads.enqueue("1", copy(), "beach.png", "Aloha fixtures/")
        val watched = uploads.observe("1", "Aloha fixtures").first()
        assertEquals(listOf(FilesUpload(id, "beach.png", FilesUploadState.Queued)), watched)
        assertEquals(emptyList<FilesUpload>(), uploads.observe("1", "elsewhere").first())
    }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val POLL_MILLIS = 20L
    }
}
