// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class MediaUploadsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val uploads = MediaUploads(context)
    private val file = File(context.cacheDir, "beach.jpg").apply { writeBytes(ByteArray(16)) }
    private val media = LocalMedia(file, "beach.jpg", "image/jpeg", null)

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker = object : Worker(appContext, workerParameters) {
                            override fun doWork() = Result.success(workDataOf(MediaUploadWorker.MEDIA_ID to "m1"))
                        }
                    },
                )
                .build(),
        )
    }

    @After
    fun close() = fixture.close()

    private fun ids(name: String) = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get().map { it.id }

    @Test
    fun `a reopened draft joins its upload or finds it cancelled, and only retrying restarts`() = runBlocking {
        val account = fixture.signIn("https://up.test/".toHttpUrl())
        val name = uploads.enqueue(account, media)
        val first = ids(name)
        uploads.enqueue(account, media, join = true)
        assertEquals(first, ids(name))
        uploads.enqueue(account, media)
        assertEquals(1, ids(name).size)
        assertNotEquals(first, ids(name))
        uploads.cancel(name)
        assertEquals(UploadState.Cancelled, uploads.observe(name).first())
        uploads.enqueue(account, media, join = true)
        assertEquals(UploadState.Cancelled, uploads.observe(name).first())
    }

    @Test
    fun `an upload done while the draft was closed is taken as it is`() = runBlocking {
        val account = fixture.signIn("https://up.test/".toHttpUrl())
        val name = uploads.enqueue(account, media)
        val sent = ids(name).single()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(sent)
        assertEquals(UploadState.Done("m1", null), uploads.observe(name).first { it is UploadState.Done })
        uploads.enqueue(account, media, join = true)
        assertEquals(listOf(sent), ids(name))
    }
}
