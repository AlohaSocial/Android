// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.MigrationListKind
import social.aloha.core.testing.SignedInFixture

/** The export routes as Social answers them: the followed accounts and statistics as CSV, the archive refused. */
private class ExportRoutes : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        asked += "${request.url.encodedPath}?${request.url.encodedQuery.orEmpty()}"
        return when {
            request.url.encodedPath.endsWith("/migration/export/following") ->
                MockResponse.Builder().body("Account address\nbob@example.social\n").build()

            request.url.encodedPath.endsWith("/statistics/export") -> MockResponse.Builder().body("day,posts\n").build()

            else -> MockResponse.Builder().code(500).body("""{"error":"export failed"}""").build()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class ExportWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val routes = ExportRoutes()
    private val server = MockWebServer().apply {
        dispatcher = routes
        start()
    }

    init {
        // the notification's cancel action asks WorkManager for its intent
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private suspend fun run(job: String, into: File): ListenableWorker.Result {
        val account = fixture.signIn(server.url("/"))
        fixture.connectNextcloud(account.id, "Basic YWxpY2U6YXBw")
        val worker = TestListenableWorkerBuilder<ExportWorker>(context)
            .setInputData(
                workDataOf(
                    ExportWorker.ACCOUNT to account.id,
                    ExportWorker.JOB to job,
                    ExportWorker.URI to into.toURI().toString(),
                ),
            )
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = ExportWorker(
                        appContext,
                        workerParameters,
                        fixture.accounts,
                        NextcloudExtras(fixture.clients),
                        Dispatchers.IO,
                    )
                },
            )
            .build()
        return worker.doWork()
    }

    @Test
    fun `a list is written into the document the reader picked`() = runBlocking {
        val following = File(context.cacheDir, "following.csv").apply { writeText("an older and much longer file") }
        val job = ExportJob.ListOf(MigrationListKind.Following).wire()
        assertEquals(ListenableWorker.Result.success(), run(job, following))
        assertEquals("Account address\nbob@example.social\n", following.readText())
    }

    @Test
    fun `statistics are exported over the window asked for`() = runBlocking {
        val csv = File(context.cacheDir, "statistics.csv")
        assertEquals(ListenableWorker.Result.success(), run(ExportJob.Statistics(30).wire(), csv))
        assertTrue(routes.asked.toString(), routes.asked.any { "/statistics/export?" in it && "days=30" in it })
    }

    @Test
    fun `a refused archive fails the work and is not tried again`() = runBlocking {
        val archive = File(context.cacheDir, "archive.zip")
        assertEquals(ListenableWorker.Result.failure(), run(ExportJob.Archive.wire(), archive))
        assertEquals(1, routes.asked.count { it.contains("/migration/export") })
    }

    @Test
    fun `a job it cannot read leaves the document alone`() = runBlocking {
        val kept = File(context.cacheDir, "kept.csv").apply { writeText("the reader's own file") }
        assertEquals(ListenableWorker.Result.failure(), run("nonsense", kept))
        assertEquals("the reader's own file", kept.readText())
    }

    @Test
    fun `a document asked for again follows the export already going`() = runBlocking {
        val exports = Exports(context)
        val twice = Uri.fromFile(File(context.cacheDir, "twice.csv"))
        val first = exports.enqueue("alice", ExportJob.Archive, twice)
        assertEquals(first, exports.enqueue("alice", ExportJob.Archive, twice))
        val other = Uri.fromFile(File(context.cacheDir, "other.csv"))
        assertNotEquals(first, exports.enqueue("alice", ExportJob.Archive, other))
    }

    @Test
    fun `every job comes back from its name`() {
        val jobs = listOf(ExportJob.Archive, ExportJob.ListOf(MigrationListKind.Lists), ExportJob.Statistics(0))
        assertEquals(jobs, jobs.map { exportJobOf(it.wire()) })
    }
}
