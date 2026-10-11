// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import java.util.TimeZone
import java.util.UUID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.nextcloud.NextcloudFile
import social.aloha.core.sync.FilesUpload
import social.aloha.core.sync.FilesUploadState

/** The Files browser in its states, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FilesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun zone() {
        // times show in the machine's zone, which would otherwise differ between machines
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private object NoFilesActions : FilesActions {
        override fun onOpen(folder: String) = Unit

        override fun onUp() = Unit

        override fun onToggle(path: String) = Unit

        override fun onSort(sort: FileSort) = Unit

        override fun onRetry() = Unit

        override fun onUpload(uri: String) = Unit

        override fun onCancelUpload(upload: FilesUpload) = Unit

        override fun onUploadFailureShown() = Unit
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) { content() } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun entry(name: String, folder: Boolean = false, mime: String? = null, size: Long = 0) = NextcloudFile(
        path = "Photos/2026/$name",
        isFolder = folder,
        mimeType = mime,
        size = size,
        modified = Instant.parse("2026-09-29T10:00:00Z"),
        etag = name,
        fileId = null,
        permissions = "RGDNVWCK",
        hasPreview = false,
        encrypted = false,
    )

    private val browsing = FilesUiState(
        folder = "Photos/2026",
        status = FilesStatus.Ready,
        rows = listOf(
            FileRow(entry("Trip #1", folder = true)),
            FileRow(entry("beach.jpg", mime = "image/jpeg", size = 2_400_000)),
            FileRow(entry("sunrise paddle.mp4", mime = "video/mp4", size = 31_000_000)),
            FileRow(entry("notes.txt", mime = "text/plain", size = 1_200), Unavailable.Type),
            FileRow(entry("diving.mov", mime = "video/mp4", size = 90_000_000), Unavailable.TooLarge(41_943_040)),
            FileRow(entry("passport.jpg", mime = "image/jpeg", size = 800_000), Unavailable.Encrypted),
        ),
        selected = listOf("Photos/2026/beach.jpg"),
        room = 4,
        canUpload = true,
        uploads = listOf(FilesUpload(UUID(0, 1), "reef.jpg", FilesUploadState.Sending(0.4f))),
    )

    @Composable
    private fun Browser(state: FilesUiState) = FilesBrowser(
        state,
        NoFilesActions,
        preview = { _, _ -> null },
        uploadTypes = arrayOf("image/*"),
        onAttach = {},
        onTypePath = {},
        onConnect = {},
        onClose = {},
    )

    @Test
    fun browsing() = capture("composer-files") { Browser(browsing) }

    @Test
    @Config(fontScale = 2f)
    fun browsingLargeFont() = capture("composer-files-font200") { Browser(browsing) }

    @Test
    fun signedOut() = capture("composer-files-signed-out") {
        Browser(FilesUiState(folder = "Photos", status = FilesStatus.SignedOut))
    }
}
