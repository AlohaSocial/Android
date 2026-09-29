// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.text.AnnotatedString
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Reaction
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/** The thread and its lists, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ThreadScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = RichTextCache()

    @Before
    fun zone() {
        // the focused post shows its full date, which would otherwise follow the machine's zone
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private object NoActions : StatusActions, ThreadScreenActions {
        override fun onOpen(statusId: String) = Unit
        override fun onProfile(accountId: String) = Unit
        override fun onLink(target: RichLinkTarget) = Unit
        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onReply(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
        override fun onBack() = Unit
        override fun onRefresh() = Unit
        override fun onMore(parentId: String) = Unit
        override fun onList(kind: StatusListKind) = Unit
        override fun onHistory() = Unit
        override fun onHistoryDismissed() = Unit
    }

    @Composable
    private fun thread(): ThreadUiState {
        val mapper = StatusRowMapper(cache, RichTextColors.fromTheme())
        fun row(id: String, reactions: List<Reaction>? = null) = mapper.map(
            StatusSamples.post().copy(id = id, reactions = reactions, editedAt = StatusSamples.NOW),
            "1",
            showContext = false,
        )
        return ThreadUiState(
            items = listOf(
                ThreadItem.Post(row("a1"), focused = false, depth = 0),
                ThreadItem.Post(row("f", listOf(Reaction("🎉", 2))), focused = true, depth = 0),
                ThreadItem.Post(row("r1"), focused = false, depth = 1),
                ThreadItem.Post(row("r2"), focused = false, depth = 2),
                ThreadItem.More("r2", count = 7, depth = 5),
            ),
            loading = false,
            now = StatusSamples.NOW,
            lists = mapOf(
                StatusListKind.FavouritedBy to 12,
                StatusListKind.BoostedBy to 5,
                StatusListKind.Reactions to 2,
            ),
            edited = true,
        )
    }

    private fun capture(
        name: String,
        settings: ThemeSettings = ThemeSettings(mode = ThemeMode.Light),
        content: @Composable () -> Unit,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme(settings) { content() } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun conversation() = capture("thread-conversation") { ThreadScreen(thread(), NoActions, NoActions) }

    @Test
    fun conversationDark() = capture("thread-conversation-dark", ThemeSettings(mode = ThemeMode.Dark)) {
        ThreadScreen(thread(), NoActions, NoActions)
    }

    @Test
    @Config(fontScale = 2f)
    fun conversationLargeFont() = capture("thread-conversation-font200") {
        ThreadScreen(thread(), NoActions, NoActions)
    }

    @Test
    fun gone() = capture("thread-gone") {
        ThreadScreen(ThreadUiState(loading = false, gone = true), NoActions, NoActions)
    }

    // the sheet is a window of its own, which only a capture of the whole screen includes
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun history() {
        val versions = listOf(
            EditVersion(StatusSamples.NOW.minusSeconds(3600), null, AnnotatedString("Aloha from the beach")),
            EditVersion(StatusSamples.NOW, "Sunburn", AnnotatedString("Aloha from the beach! Bring sunscreen.")),
        )
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) {
                ThreadScreen(thread().copy(history = versions), NoActions, NoActions)
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/thread-history.png")
    }

    @Test
    fun favouritedBy() = capture("thread-list-accounts") {
        val mapper = StatusRowMapper(cache, RichTextColors.fromTheme())
        val people = listOf(StatusSamples.alice, StatusSamples.bob).map {
            StatusListState.Person(mapper.author(it), it.emojis)
        }
        StatusListScreen(StatusListKind.FavouritedBy, StatusListState.Accounts(people), NoActions, onBack = {
        }, onRetry = {})
    }

    @Test
    fun reactions() = capture("thread-list-reactions") {
        StatusListScreen(
            StatusListKind.Reactions,
            StatusListState.Reactions(listOf(Reaction("🎉", 2), Reaction("❤️", 1))),
            NoActions,
            onBack = {},
            onRetry = {},
        )
    }

    @Test
    fun listFailed() = capture("thread-list-failed") {
        StatusListScreen(StatusListKind.Quotes, StatusListState.Failed(Trouble.Offline), NoActions, onBack = {
        }, onRetry = {})
    }
}
