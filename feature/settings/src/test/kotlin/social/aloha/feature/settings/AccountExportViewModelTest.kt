// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import org.robolectric.annotation.Config
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.MigrationListKind
import social.aloha.core.testing.SignedInFixture

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
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/migration/export/following") ->
                    MockResponse.Builder().body("Account address\nbob@example.social\n").build()

                else -> MockResponse.Builder().code(500).body("""{"error":"export failed"}""").build()
            }
        }
        start()
    }

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    private fun open(): AccountExportViewModel = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        fixture.connectNextcloud(reader.id, "Basic YWxpY2U6YXBw")
        AccountExportViewModel(reader.id, context, fixture.accounts, NextcloudExtras(fixture.clients))
            .also { store.put("export", it) }
    }

    @Test
    fun `a list is written where the reader chose, and a refused archive says so`() = runBlocking {
        val viewModel = open()
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.status == PageStatus.Ready } }
        val following = File(context.cacheDir, "following.csv")
        viewModel.onExport(ExportItem(MigrationListKind.Following), following.toURI().toString())
        withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.saved != null } }
        assertEquals("Account address\nbob@example.social\n", following.readText())

        viewModel.onResultShown()
        viewModel.onExport(ExportItem(), File(context.cacheDir, "archive.zip").toURI().toString())
        assertTrue(withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.failed } }.saved == null)
    }

    @Test
    fun `the archive is named after the account and the day, a list after itself`() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals("social-alice-2026-10-10.zip", ExportItem().fileName("alice", today))
        assertEquals("mutes.csv", ExportItem(MigrationListKind.Mutes).fileName("alice", today))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
