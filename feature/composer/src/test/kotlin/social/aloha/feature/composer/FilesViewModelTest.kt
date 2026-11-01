// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.model.ServerLimits
import social.aloha.core.network.HttpExchange
import social.aloha.core.network.RateLimiter
import social.aloha.core.nextcloud.NextcloudFile
import social.aloha.core.nextcloud.NextcloudFiles
import social.aloha.core.sync.FilesUploads
import social.aloha.core.testing.DavServer
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.SignedInFixture

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FilesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val dav = DavServer()
    private val server = MockWebServer().apply {
        dispatcher = dav
        start()
    }
    private val store = ViewModelStore()

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        fixture.close()
        server.close()
    }

    private fun open(room: Int = 4): FilesViewModel = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        fixture.connectNextcloud(reader.id, DavServer.BASIC)
        val files = NextcloudFiles(
            HttpExchange(OkHttpClient(), RateLimiter(nowMillis = { 0L }), Dispatchers.IO),
            fixture.vault,
            Dispatchers.IO,
        )
        FilesViewModel(reader.id, context, fixture.accounts, files, FilesUploads(context)).also {
            store.put("files", it)
            it.onShown(room)
        }
    }

    private fun FilesViewModel.shown(folder: String) = runBlocking {
        withTimeout(WAIT_MILLIS) { uiState.first { it.folder == folder && it.status != FilesStatus.Loading } }
    }

    @Test
    fun `a folder shows its folders first, and says which files the post cannot take`() {
        val files = open()
        files.onOpen("Aloha fixtures")
        val state = files.shown("Aloha fixtures")
        assertEquals(FilesStatus.Ready, state.status)
        assertEquals(
            listOf("100% ?real", "Trip #1", "notes.txt", "Ünïcödé café.png"),
            state.rows.map { it.file.name },
        )
        assertEquals(Unavailable.Type, state.rows.first { it.file.name == "notes.txt" }.unavailable)
        assertNull(state.rows.first { it.file.name == "Ünïcödé café.png" }.unavailable)
        assertTrue(state.canUpload)
    }

    @Test
    fun `picking stops at the post's room, and a picked file can be put back`() {
        val files = open(room = 1)
        files.onOpen("Aloha fixtures")
        files.shown("Aloha fixtures")
        files.onToggle("Aloha fixtures/Ünïcödé café.png")
        files.onToggle("Aloha fixtures/another.png")
        assertEquals(listOf("Aloha fixtures/Ünïcödé café.png"), files.uiState.value.selected)
        files.onToggle("Aloha fixtures/Ünïcödé café.png")
        assertEquals(emptyList<String>(), files.uiState.value.selected)
    }

    @Test
    fun `back goes up a folder, and opening again keeps the folder shown`() {
        val files = open()
        files.onOpen("Aloha fixtures/Trip #1")
        files.shown("Aloha fixtures/Trip #1")
        files.onUp()
        assertEquals(FilesStatus.Ready, files.shown("Aloha fixtures").status)
        files.onShown(room = 2)
        assertEquals("Aloha fixtures", files.uiState.value.folder)
    }

    @Test
    fun `a missing folder is gone, and a refused password asks for the connection again`() {
        val files = open()
        files.onOpen("no such folder")
        assertEquals(FilesStatus.Gone, files.shown("no such folder").status)
        dav.answer("PROPFIND", "/remote.php/dav/files/alice/Aloha%20fixtures/", 401)
        files.onOpen("Aloha fixtures")
        assertEquals(FilesStatus.SignedOut, files.shown("Aloha fixtures").status)
    }

    @Test
    fun `a file is checked as Social checks it before copying it into a post`() {
        // a server that takes neither HEIC nor QuickTime, which an upload would have converted
        val limits = ServerLimits.MastodonDefaults.copy(supportedMimeTypes = listOf("image/png", "video/mp4"))
        fun file(mime: String, size: Long = 1_000, permissions: String = "RGDNVW", encrypted: Boolean = false) =
            NextcloudFile("a", false, mime, size, Instant.EPOCH, "e", 1, permissions, false, encrypted)

        assertNull(FilesViewModel.row(file("image/png"), limits).unavailable)
        assertEquals(Unavailable.Encrypted, FilesViewModel.row(file("image/png", encrypted = true), limits).unavailable)
        assertEquals(
            Unavailable.Unreadable,
            FilesViewModel.row(file("image/png", permissions = "RDNVW"), limits).unavailable,
        )
        assertEquals(Unavailable.Type, FilesViewModel.row(file("image/heic"), limits).unavailable)
        assertEquals(Unavailable.Type, FilesViewModel.row(file("video/quicktime"), limits).unavailable)
        assertEquals(
            Unavailable.TooLarge(limits.imageSizeLimit),
            FilesViewModel.row(file("image/png", size = limits.imageSizeLimit + 1), limits).unavailable,
        )
        assertEquals(
            Unavailable.TooLarge(limits.videoSizeLimit),
            FilesViewModel.row(file("video/mp4", size = limits.videoSizeLimit + 1), limits).unavailable,
        )
    }

    @Test
    fun `a picked file's name is made one Nextcloud saves`() {
        assertEquals("beach.jpg", FilesViewModel.fileName("beach.jpg"))
        assertEquals("holiday_beach.jpg", FilesViewModel.fileName("holiday/beach.jpg"))
        assertEquals("upload", FilesViewModel.fileName(".."))
        assertEquals("upload", FilesViewModel.fileName(" "))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
