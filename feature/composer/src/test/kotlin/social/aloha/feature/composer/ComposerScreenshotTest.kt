// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Visibility
import social.aloha.core.sync.UploadState

/** The composer in its states, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ComposerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private object NoActions : ComposerActions {
        override fun onClose() = Unit
        override fun onPost() = Unit
        override fun onText(index: Int, value: TextFieldValue) = Unit
        override fun onSpoiler(shown: Boolean, text: String) = Unit
        override fun onVisibility(visibility: Visibility) = Unit
        override fun onLanguage(language: String?) = Unit
        override fun onQuotePolicy(policy: QuotePolicy) = Unit
        override fun onSuggestion(suggestion: Suggestion) = Unit
        override fun onEmoji(emoji: CustomEmoji) = Unit
        override fun onAddSegment() = Unit
        override fun onRemoveSegment(index: Int) = Unit
        override fun onAuthor(id: String) = Unit
        override fun onPickMedia() = Unit
        override fun onPickFiles() = Unit
        override fun onEditMedia(id: String) = Unit
        override fun onRemoveMedia(id: String) = Unit
        override fun onRetryMedia(id: String) = Unit
        override fun onSensitive(sensitive: Boolean) = Unit
    }

    private val alice = Author("a", "@alice@cloud.example", "Alice", null)

    private val fresh = ComposerUiState(
        ready = true,
        author = alice,
        authors = listOf(alice, Author("b", "@alice@mastodon.example", "Alice", null)),
        language = "en",
        remaining = listOf(4983),
        quotePolicies = QuotePolicy.entries,
    )

    private fun value(text: String) = TextFieldValue(text, TextRange(text.length))

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

    @Composable
    private fun NewPost(settings: ComposerUiState = fresh) = ComposerScreen(
        settings,
        listOf(value("Aloha from the beach! #surf @bo")),
        spoiler = "",
        actions = NoActions,
    )

    @Composable
    private fun ReplyInThread() = ComposerScreen(
        fresh.copy(
            reply = ReplyContext("@bob@remote.example", "Who is up for a swim at sunrise?"),
            visibility = Visibility.Private,
            visibilities = listOf(Visibility.Private, Visibility.Direct),
            visibilityClamped = true,
            spoilerShown = true,
            remaining = listOf(120, -12),
            games = listOf(ComposerGames.Kind.Dice),
            suggestions = listOf(Suggestion("@bob@remote.example", "Bob · @bob@remote.example", null)),
        ),
        listOf(value("@bob@remote.example I'm in, /dice decides the time"), value("And bring a board @bo")),
        spoiler = "Early mornings",
        actions = NoActions,
    )

    @Test
    fun newPost() = capture("composer-new") { NewPost() }

    @Test
    fun newPostDark() = capture("composer-new-dark", ThemeSettings(mode = ThemeMode.Dark)) { NewPost() }

    @Test
    fun replyThread() = capture("composer-reply-thread") { ReplyInThread() }

    @Test
    @Config(fontScale = 2f)
    fun replyLargeFont() = capture("composer-reply-font200") { ReplyInThread() }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun tablet() = capture("composer-tablet") { ReplyInThread() }

    @Test
    fun posting() = capture("composer-posting") { NewPost(fresh.copy(posting = true)) }

    // four tiles in the states a writer meets: described, undescribed, uploading and failed
    @Test
    fun media() = capture("composer-media") {
        fun tile(id: String, upload: UploadState, description: String = "") =
            Attachment(id, File("$id.jpg"), "$id.jpg", "image/jpeg", upload, description)
        NewPost(
            fresh.copy(
                attachments = listOf(
                    listOf(
                        tile("beach", UploadState.Done("1", null), "Surfers at sunrise on a wide beach"),
                        tile("board", UploadState.Done("2", null)),
                        tile("wave", UploadState.Sending(0.4f)),
                        tile("sunset", UploadState.Failed),
                    ),
                ),
                mediaSensitive = true,
            ),
        )
    }
}
