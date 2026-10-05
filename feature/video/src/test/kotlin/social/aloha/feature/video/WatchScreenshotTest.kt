// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.MediaMeta
import social.aloha.core.model.VideoChapter
import social.aloha.core.model.VideoDetails
import social.aloha.core.model.VideoSource
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.RoutedStatusActions
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WatchScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val nowhere = object : StatusNavigation {
        override fun openThread(statusId: String, history: Boolean) = Unit
        override fun openProfile(accountId: String?, acct: String?) = Unit
        override fun openTag(name: String) = Unit
        override fun openWeb(url: String) = Unit
        override fun openComposer(replyToId: String?) = Unit
        override fun editPost(statusId: String, redraft: Boolean) = Unit
        override fun report(accountId: String, handle: String, statusId: String?) = Unit
    }

    private val rowActions = object : RoutedStatusActions(
        ApplicationProvider.getApplicationContext<Context>(),
        navigation = { nowhere },
        onCopied = {},
        onDeleteAsked = {},
    ) {
        override fun onMute(row: StatusRowUi) = Unit
        override fun onPin(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
    }

    private val clip = MediaAttachment(
        "m",
        AttachmentKind.Video,
        url = "https://cloud.example/m.mp4",
        blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
        meta = MediaMeta(original = MediaDimensions(width = 1920, height = 1080, duration = 754.0)),
    )

    @Composable
    private fun rows(): List<StatusRowUi> {
        val mapper = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme())
        val video = StatusSamples.post("<p>A walk along the harbour at dawn.</p>").copy(
            id = "v1",
            repliesCount = 1,
            mediaAttachments = listOf(clip),
            video = VideoDetails(title = "Harbour at dawn", views = 1_204, likes = 87, dislikes = 2),
        )
        val comment = StatusSamples.reply.copy(id = "c1")
        return listOf(mapper.map(video, "1"), mapper.map(comment, "1"))
    }

    private fun capture(
        name: String,
        settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        content: @Composable () -> Unit,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(settings) { Surface { content() } } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val none = WatchScreenActions({}, {}, {}, {}, {}, {})

    @Composable
    private fun Watch(tabletop: Boolean) {
        val (video, comment) = rows()
        WatchScreen(
            WatchUiState(
                video = video,
                sources = listOf(VideoSource("https://cloud.example/m.mp4", hls = false)),
                chapters = listOf(VideoChapter(0.0, "Start"), VideoChapter(90.0, "The harbour")),
                comments = listOf(comment),
                following = false,
                loading = false,
            ),
            StatusSamples.NOW,
            none,
            rowActions,
            SnackbarHostState(),
            tabletop = tabletop,
        ) {
            // the player draws video frames, which a screenshot has none of
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        }
    }

    @Test
    fun watch() = capture("watch") { Watch(tabletop = false) }

    @Test
    @Config(fontScale = 2f)
    fun watchLargeFont() = capture("watch-font200") { Watch(tabletop = false) }

    // a foldable half open like a laptop: the video above the fold, the rest scrolling below it
    @Test
    fun watchTabletop() = capture("watch-tabletop") { Watch(tabletop = true) }

    @Test
    fun continueWatching() = capture("continue-watching", ThemeSettings(mode = ThemeMode.Dark)) {
        val (video) = rows()
        ContinueWatching(listOf(Unfinished(video, 0.4), Unfinished(video.copy(statusId = "v2", rowId = "v2"), 0.8)), {
        }, {})
    }
}
