// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import social.aloha.core.model.MigrationListKind
import social.aloha.core.sync.Exports
import social.aloha.core.testing.SignedInFixture

/** Stands in for the export worker: everything is saved but the archive, which the server refuses. */
private val ExportsSucceedButArchive = object : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker = object : Worker(appContext, workerParameters) {
        override fun doWork() = if (inputData.getString("job") == "archive") Result.failure() else Result.success()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AccountExportViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val store = ViewModelStore()

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(ExportsSucceedButArchive)
                .build(),
        )
    }

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        fixture.close()
    }

    private fun open(): AccountExportViewModel = runBlocking {
        val reader = fixture.signIn("https://social.example".toHttpUrl())
        fixture.connectNextcloud(reader.id, "Basic YWxpY2U6YXBw")
        AccountExportViewModel(reader.id, fixture.accounts, Exports(context)).also { store.put("export", it) }
    }

    private fun networkUp() = WorkManagerTestInitHelper.getTestDriver(context)?.let { driver ->
        WorkManager.getInstance(context).getWorkInfosByTag("export").get()
            .forEach { driver.setAllConstraintsMet(it.id) }
    }

    @Test
    fun `a saved list and a refused archive are each told once they are done`() = runBlocking {
        val viewModel = open()
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        viewModel.onExport(ExportItem(MigrationListKind.Following), "content://documents/following.csv")
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.working == ExportItem(MigrationListKind.Following) } }
        waitForWork()
        networkUp()
        val saved = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.saved != null } }
        assertEquals(ExportItem(MigrationListKind.Following), saved.saved)

        viewModel.onResultShown()
        viewModel.onExport(ExportItem(), "content://documents/archive.zip")
        waitForWork(count = 2)
        networkUp()
        assertTrue(withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.failed } }.saved == null)
    }

    private suspend fun waitForWork(count: Int = 1) = withTimeout(WAIT_MILLIS) {
        while (WorkManager.getInstance(context).getWorkInfosByTag("export").get().size < count) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(POLL_MILLIS)
        }
    }

    @Test
    fun `the archive is named after the account and the day, a list after itself`() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals("social-alice-2026-10-10.zip", ExportItem().fileName("alice", today))
        assertEquals("mutes.csv", ExportItem(MigrationListKind.Mutes).fileName("alice", today))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
