// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.time.Instant
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.compose.DraftMedia
import social.aloha.core.data.compose.DraftPoll
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.DraftSegment
import social.aloha.core.data.compose.OutboxEntry
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.GifEntry
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.OutboxState
import social.aloha.core.model.ScheduledStatus
import social.aloha.core.model.ScheduledStatusParams
import social.aloha.core.model.Visibility
import social.aloha.core.model.Writing
import social.aloha.core.sync.UploadState

/** The composer in its states, each also run through the Accessibility Test Framework checks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ComposerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun zone() {
        // scheduled times show in the machine's zone, which would otherwise differ between machines
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private object NoActions : ComposerActions {
        override fun onClose() = Unit
        override fun onPost() = Unit
        override fun onText(index: Int, value: TextFieldValue) = Unit
        override fun onSpoiler(shown: Boolean, text: String) = Unit
        override fun onVisibility(visibility: Visibility) = Unit
        override fun onLanguage(language: String?) = Unit
        override fun onQuotePolicy(policy: QuotePolicy) = Unit
        override fun onSuggestion(suggestion: Suggestion) = Unit
        override fun onFindPeople(query: String) = Unit
        override fun onEmoji(emoji: CustomEmoji) = Unit
        override fun onAddSegment() = Unit
        override fun onRemoveSegment(index: Int) = Unit
        override fun onAuthor(id: String) = Unit
        override fun onPickMedia() = Unit
        override fun onPickFiles() = Unit
        override fun onCapture(capture: Capture) = Unit

        override fun onGifs() = Unit

        override fun onNextcloudFile() = Unit

        override fun onPaste() = Unit

        override fun onPoll(poll: PollUi?) = Unit

        override fun onPickSchedule() = Unit

        override fun onSchedule(at: Instant?) = Unit

        override fun onScheduledPosts() = Unit

        override fun onDrafts() = Unit
        override fun onEditMedia(id: String) = Unit
        override fun onRemoveMedia(id: String) = Unit
        override fun onRetryMedia(id: String) = Unit
        override fun onSensitive(sensitive: Boolean) = Unit
        override fun onCard(on: Boolean) = Unit
        override fun onStory(on: Boolean) = Unit
        override fun onStorySeconds(seconds: Int) = Unit
        override fun onCardBackground(index: Int) = Unit
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

    @Test
    fun writingSettings() = capture("composer-settings") {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WritingContent(
                    warn = true,
                    onWarn = {},
                    writing = Writing(confirmBeforePosting = true, numberThreads = true, language = "de"),
                    languages = listOf("en", "de"),
                    onChange = {},
                )
            }
        }
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
            completions = CompletionsUi(
                CompletionKind.Account,
                "bo",
                listOf(Suggestion("@bob@remote.example", "Bob · @bob@remote.example", null)),
            ),
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
    fun overLimitWhileLooking() = capture("composer-over-limit") {
        ComposerScreen(
            fresh.copy(
                remaining = listOf(-6),
                overFrom = listOf(30),
                completions = CompletionsUi(CompletionKind.Account, "kai", loading = true),
            ),
            listOf(value("Paddling out at dawn, who’s in? @kai")),
            spoiler = "",
            actions = NoActions,
        )
    }

    @Test
    fun nobodyFound() = capture("composer-find-people") {
        ComposerScreen(
            fresh.copy(completions = CompletionsUi(CompletionKind.Account, "kai")),
            listOf(value("Paddling out at dawn @kai")),
            spoiler = "",
            actions = NoActions,
        )
    }

    @Test
    @Config(fontScale = 2f)
    fun replyLargeFont() = capture("composer-reply-font200") { ReplyInThread() }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun tablet() = capture("composer-tablet") { ReplyInThread() }

    @Test
    fun posting() = capture("composer-posting") { NewPost(fresh.copy(posting = true)) }

    @Test
    fun quote() = capture("composer-quote") {
        val excerpt = "Surf report: waist high and glassy, going out at seven."
        val quote = QuoteUi("q", "Bob", excerpt, "https://example.test/q", Visibility.Unlisted, own = false)
        NewPost(fresh.copy(quote = quote, quoteNotice = QuoteNotice.Unlisted))
    }

    @Test
    fun story() = capture("composer-story") {
        val picture = Attachment("beach", File("beach.jpg"), "beach.jpg", "image/jpeg", UploadState.Done("1", null))
        NewPost(fresh.copy(attachments = listOf(listOf(picture)), storyFits = true, asStory = true, storySeconds = 10))
    }

    @Test
    fun card() = capture("composer-card") {
        val text = "Paddle out at sunrise, back for breakfast 🌅"
        val backgrounds = listOf(0xFF8E4A3A.toInt(), 0xFF00605A.toInt(), 0xFF3A3F9F.toInt())
        ComposerScreen(
            fresh.copy(
                cardFits = true,
                card = CardUi(
                    on = true,
                    background = 0,
                    backgrounds = backgrounds,
                    preview = CardRenderer.render(text, backgrounds.first(), 360).asImageBitmap(),
                ),
            ),
            listOf(value(text)),
            spoiler = "",
            actions = NoActions,
        )
    }

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

    @Test
    fun gifs() = captureScreen("composer-gifs") {
        val gifs = (1..9).map { GifEntry("g$it", "Waves $it") }
        GifSheet(GifsUi("waves", gifs, attribution = "GIFs from the Nextcloud library"), {}, {}, {}, {})
    }

    // a scheduled post with a poll whose second choice runs over the limit
    @Test
    fun pollScheduled() = capture("composer-poll-scheduled") {
        NewPost(
            fresh.copy(
                poll = PollUi(options = listOf("North shore", "South shore, where the long waves roll in at dawn")),
                maxPollOptionCharacters = 25,
                pollDurations = PollUi.durations(300, 604_800),
                scheduledAt = Instant.parse("2030-06-01T07:30:00Z"),
            ),
        )
    }

    @Test
    fun scheduledPosts() = capture("scheduled-posts") {
        val params = ScheduledStatusParams(text = "Sunrise paddle out, who is in? Meet at the north end of the beach.")
        ScheduledPostsScreen(
            ScheduledUiState(
                posts = listOf(
                    ScheduledStatus("1", params, Instant.parse("2030-06-01T07:30:00Z")),
                    ScheduledStatus(
                        "2",
                        ScheduledStatusParams(text = "Board swap this weekend", spoilerText = "Gear talk"),
                        Instant.parse("2030-06-02T18:00:00Z"),
                        mediaAttachments = listOf(
                            MediaAttachment("m", AttachmentKind.Image, "https://example.test/a.jpg"),
                        ),
                    ),
                ),
                loading = false,
            ),
            onBack = {},
            onMove = {},
            onDelete = {},
            onRetry = {},
            snackbars = SnackbarHostState(),
        )
    }

    // a draft of each kind, and a post in each state of the outbox
    @Test
    fun drafts() = capture("drafts") {
        DraftsScreen(sampleDrafts(), DraftsActions({}, {}, {}, {}))
    }

    private fun sampleDrafts(): List<OutboxEntry> {
        val at = Instant.parse("2030-06-01T07:30:00Z")
        return listOf(
            OutboxEntry(
                "1",
                "a",
                OutboxState.Draft,
                DraftPost(
                    segments = listOf(DraftSegment("Sunrise paddle out, who is in?"), DraftSegment("Bring a board")),
                    poll = DraftPoll(listOf("Yes", "No"), 3_600),
                ),
                at,
                null,
            ),
            OutboxEntry(
                "2",
                "a",
                OutboxState.Draft,
                DraftPost(
                    segments = listOf(DraftSegment("", listOf(DraftMedia("wave.jpg", "image/jpeg")))),
                    replyToId = "9",
                    spoiler = "Wipeout",
                ),
                at.minusSeconds(86_400),
                null,
            ),
            OutboxEntry("3", "a", OutboxState.Queued, DraftPost(listOf(DraftSegment("On the way"))), at, null),
            OutboxEntry(
                "4",
                "a",
                OutboxState.Failed,
                DraftPost(listOf(DraftSegment("Too long, way too long"))),
                at,
                "Text character limit of 500 exceeded",
            ),
            OutboxEntry("5", "a", OutboxState.Paused, DraftPost(listOf(DraftSegment("Later"))), at, null),
        )
    }

    // a sheet is a window of its own, so the whole screen is captured
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        compose.setContent { AlohaTheme(ThemeSettings(mode = ThemeMode.Light)) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }
}
