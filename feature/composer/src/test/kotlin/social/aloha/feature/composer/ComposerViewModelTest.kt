// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import java.net.URLDecoder
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.PostSender
import social.aloha.core.data.compose.ScheduledPosts
import social.aloha.core.data.stories.Stories
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.database.OutboxDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.OutboxState
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.Visibility
import social.aloha.core.model.Writing
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.network.RateLimiter
import social.aloha.core.sync.MediaUploads
import social.aloha.core.sync.PostQueue
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class ComposerViewModelTest : ComposerTestSetup() {
    @Test
    fun `a reply starts with its author and everyone it mentioned, never oneself, and reaches no further`() =
        runBlocking {
            posts.parent = posts.status("p", visibility = "private", mentions = listOf("6" to "alice", "8" to "carol"))
            val viewModel = open(key = { ComposerKey(it, replyToId = "p") })
            val state = viewModel.await { it.ready }
            assertEquals(listOf(Visibility.Private, Visibility.Direct), state.visibilities)
            assertTrue(state.visibilityClamped)
            assertEquals(Visibility.Private, state.visibility)
            assertTrue(viewModel.segments[0].text.endsWith("@carol "))
            assertTrue("@alice" !in viewModel.segments[0].text)
        }

    @Test
    fun `a thread that fails part way resumes without posting anything twice`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "one")
        viewModel.onSegments()
        viewModel.type(1, "two")
        viewModel.onSegments()
        viewModel.type(2, "three")
        viewModel.await { it.remaining.size == 3 }
        script += listOf(200, 500)
        viewModel.onPost()
        val stopped = viewModel.await { !it.posting && it.failure != null }
        assertEquals(1, stopped.posted)
        assertTrue(stopped.failure is PostFailure.Unreached)

        viewModel.onPost()
        viewModel.await { it.done }
        val texts = posts.sent.map { it.second["status"] }
        assertEquals(listOf("one", "two", "two", "three"), texts)
        // the retried segment went with the key it had the first time, and answered the one before it
        assertEquals(posts.sent[1].first, posts.sent[2].first)
        assertEquals(listOf(null, "s1", "s1", "s2"), posts.sent.map { it.second["in_reply_to_id"] })
    }

    @Test
    fun `games are played once and sent as text, and a changed post gets a new key`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "/flip")
        assertEquals(listOf(ComposerGames.Kind.Flip), viewModel.await { it.games.isNotEmpty() }.games)
        script += 500
        viewModel.onPost()
        viewModel.await { !it.posting && it.failure != null }
        viewModel.onPost()
        viewModel.await { it.done }
        val (first, second) = posts.sent
        assertEquals(first.second["status"], second.second["status"])
        assertTrue(first.second["status"]!!.startsWith("🪙 "))
        assertEquals(first.first, second.first)
    }

    @Test
    fun `a post the server refuses keeps its text and says why`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "too long, says the server")
        viewModel.await { it.canPost }
        script += 422
        viewModel.onPost()
        val state = viewModel.await { it.failure != null }
        assertEquals(PostFailure.Refused("a post may not be longer"), state.failure)
        assertEquals("too long, says the server", viewModel.segments[0].text)
        assertEquals(0, state.posted)
    }

    @Test
    fun `nextcloud social counts what it enforces, code points at full length`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "👨‍👩‍👧‍👦 https://example.test/a/long/path")
        val state = viewModel.await { it.remaining.first() < 5000 }
        assertEquals(5000 - 7 - 1 - 32, state.remaining.first())
    }

    @Test
    fun `the gif library pages to its end, and a gif picked goes out with the post`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready && it.gifLibrary }
        viewModel.library.onQuery("")
        withTimeout(10.seconds) { viewModel.library.gifs.first { it.gifs.size == 40 && !it.loading } }
        viewModel.library.onMore()
        withTimeout(10.seconds) { viewModel.library.gifs.first { it.gifs.size == 80 && !it.loading } }
        viewModel.library.onMore()
        val all = withTimeout(10.seconds) { viewModel.library.gifs.first { it.end } }
        assertEquals(85, all.gifs.size)
        assertEquals("Library", all.attribution)
        viewModel.library.onGif(all.gifs.first())
        val attached = viewModel.await { it.attachments.first().isNotEmpty() }.attachments.first().single()
        assertEquals("m-g0", attached.mediaId)
        assertEquals("GIF 0", attached.description)
        viewModel.type(0, "Look")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals("m-g0", posts.sent.single().second["media_ids[]"])
    }

    @Test
    fun `one picture shared as a story goes to the stories route with its caption, and no post goes out`() =
        runBlocking {
            val viewModel = open(key = { ComposerKey(it, story = true) })
            viewModel.await { it.ready }
            viewModel.library.onNextcloudFile("Photos/beach.jpg")
            viewModel.type(0, "Sunset")
            viewModel.await { it.canPost && it.storyFits && it.asStory }
            // a picture shows for as long as the writer picks
            viewModel.story.onSeconds(10)
            viewModel.await { it.storySeconds == 10 && it.storyLengthPicked }
            viewModel.onPost()
            viewModel.await { it.done }
            assertEquals(
                mapOf("media_id" to "m-Photos/beach.jpg", "duration" to "10", "caption" to "Sunset"),
                posts.stories.single(),
            )
            assertTrue(posts.sent.isEmpty())
        }

    @Test
    fun `a second picture makes it a post again, not a story`() = runBlocking {
        val viewModel = open(key = { ComposerKey(it, story = true) })
        viewModel.await { it.ready }
        viewModel.library.onNextcloudFile("Photos/beach.jpg")
        viewModel.await { it.storyFits }
        viewModel.library.onNextcloudFile("Photos/reef.jpg")
        assertFalse(viewModel.await { it.attachments.first().size == 2 }.storyFits)
    }

    @Test
    fun `a nextcloud file is attached by its path from the root, and one not there says so`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.library.onNextcloudFile(" /Photos/beach.jpg ")
        val attached = viewModel.await { it.attachments.first().isNotEmpty() }.attachments.first().single()
        assertEquals("m-Photos/beach.jpg", attached.mediaId)
        assertEquals("beach.jpg", attached.fileName)
        assertEquals(listOf("Photos/beach.jpg"), viewModel.library.recentPaths())
        viewModel.library.onNextcloudFile("missing.jpg")
        assertEquals(AttachFailure.NotFound, viewModel.await { it.attachFailure != null }.attachFailure)
    }

    @Test
    fun `a poll goes out with the opening post and takes the place of its media`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Which beach?")
        viewModel.poll.value = PollUi(options = listOf(" North ", "South", ""), seconds = 3_600, multiple = true)
        viewModel.await { it.poll != null && it.canPost }
        viewModel.library.onNextcloudFile("Photos/beach.jpg")
        viewModel.onPost()
        viewModel.await { it.done }
        val form = posts.sent.single().second
        assertEquals("South", form["poll[options][]"])
        assertEquals("3600", form["poll[expires_in]"])
        assertEquals("true", form["poll[multiple]"])
        assertEquals(null, form["media_ids[]"])
    }

    @Test
    fun `a poll with fewer than two different choices cannot be posted`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Which beach?")
        viewModel.poll.value = PollUi(options = listOf("North", " North"))
        assertEquals(false, viewModel.await { it.poll != null }.canPost)
    }

    @Test
    fun `a scheduled post is scheduled, not posted`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Later")
        val at = java.time.Instant.parse("2030-01-01T09:00:00Z")
        viewModel.scheduledAt.value = at
        viewModel.await { it.scheduledAt != null && it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals(at.toString(), posts.sent.single().second["scheduled_at"])
    }

    @Test
    fun `what is written is kept as a draft and comes back as it was`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Half a thought")
        viewModel.onVisibility(Visibility.Unlisted)
        viewModel.poll.value = PollUi(options = listOf("Yes", "No"), seconds = 3_600)
        val kept = withTimeout(10.seconds) {
            outbox.observe(accounts.all().single().id).first { list -> list.any { it.post.poll != null } }
        }.single()
        assertEquals("Half a thought", kept.post.segments.single().text)
        val again = open(key = { ComposerKey(it, draftId = kept.id) })
        // the poll reaches the state through a flow of its own, a moment apart from the rest
        val state = again.await { it.ready && it.poll != null }
        assertEquals("Half a thought", again.segments.single().text)
        assertEquals(Visibility.Unlisted, state.visibility)
        assertEquals(listOf("Yes", "No"), state.poll?.options)
    }

    @Test
    fun `a posted draft is gone`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Out it goes")
        val reader = accounts.all().single().id
        withTimeout(10.seconds) { outbox.observe(reader).first { it.isNotEmpty() } }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals(emptyList<Any>(), withTimeout(10.seconds) { outbox.observe(reader).first { it.isEmpty() } })
    }

    @Test
    fun `a post with no network goes to the outbox, keyed, and out later`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "From the reef /flip")
        viewModel.await { it.canPost }
        server.close()
        viewModel.onPost()
        assertEquals(true, viewModel.await { it.done }.queued)
        val queued = outbox.observe(accounts.all().single().id).first().single()
        assertEquals(OutboxState.Queued, queued.state)
        val segment = queued.post.segments.single()
        assertEquals(true, segment.key != null)
        // the game is played once, now, so the post the outbox sends is the one the writer saw
        assertEquals(false, segment.sent.orEmpty().contains("/flip"))
    }

    @Test
    fun `an edit starts from the post as written, changes it in place, and carries its media's words`() = runBlocking {
        val viewModel = open(key = { ComposerKey(it, editId = "mine") })
        val state = viewModel.await { it.ready && it.attachments.first().isNotEmpty() }
        assertEquals(true, state.editing)
        assertEquals("As I wrote it", viewModel.segments.single().text)
        assertEquals(Visibility.Unlisted, state.visibility)
        val picture = state.attachments.first().single()
        viewModel.attachments.describe(picture.id, "New words", null)
        viewModel.type(0, "As I meant it")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        val form = posts.sent.single().second
        assertEquals("As I meant it", form["status"])
        assertEquals("m1", form["media_attributes[][id]"])
        assertEquals("New words", form["media_attributes[][description]"])
    }

    @Test
    fun `a redraft deletes the original only once the new post is out`() = runBlocking {
        val viewModel = open(key = { ComposerKey(it, redraftId = "gone") })
        val state = viewModel.await { it.ready && it.attachments.first().isNotEmpty() }
        assertEquals(false, state.editing)
        assertEquals("As I wrote it", viewModel.segments.single().text)
        assertEquals("m1", state.attachments.first().single().mediaId)
        assertEquals("Old words", state.attachments.first().single().description)
        assertEquals(emptyList<String>(), posts.deleted)
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals("m1", posts.sent.single().second["media_ids[]"])
        withTimeout(10.seconds) { while (posts.deleted.isEmpty()) delay(10) }
        assertEquals(listOf("gone"), posts.deleted)
    }

    @Test
    fun `a post with nothing in it cannot go, nor a thread with an empty segment`() = runBlocking {
        val viewModel = open(key = { ComposerKey(it) })
        assertFalse(viewModel.await { it.ready }.canPost)
        viewModel.type(0, "One")
        viewModel.await { it.canPost }
        viewModel.onSegments()
        Snapshot.sendApplyNotifications()
        viewModel.await { !it.canPost }
        viewModel.type(1, "Two")
        assertEquals(2, viewModel.await { it.canPost }.remaining.size)
    }

    @Test
    fun `what another app shared starts a new post`() = runBlocking {
        val shared = "Surf report https://surf.example/today"
        val viewModel = open(key = { ComposerKey(it, sharedText = shared) })
        viewModel.await { it.ready }
        assertEquals(shared, viewModel.segments.single().text)
    }

    @Test
    fun `a thread cut off half way is kept with what went out, and carries on without posting twice`() = runBlocking {
        script += listOf(200, 500)
        val viewModel = open(key = { ComposerKey(it, draftId = "thread") })
        viewModel.await { it.ready }
        viewModel.type(0, "One")
        viewModel.onSegments()
        viewModel.type(1, "Two")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.posted == 1 && !it.posting }
        val reader = accounts.all().single().id
        val kept = withTimeout(10.seconds) {
            outbox.observe(reader).first { list -> list.any { it.post.postedIds.isNotEmpty() } }
        }.single().post
        assertEquals(listOf("s1"), kept.postedIds)
        val again = open(key = { ComposerKey(it, draftId = "thread") })
        again.await { it.ready && it.posted == 1 }
        // the restored text reaches the state on the next frame
        Snapshot.sendApplyNotifications()
        again.await { it.canPost }
        again.onPost()
        again.await { it.done }
        // the second segment went twice under its one key; the first never went again
        val keys = posts.sent.map { it.first }
        assertEquals(3, keys.size)
        assertEquals(keys[1], keys[2])
        assertEquals("One", posts.sent[0].second["status"])
        assertEquals("Two", posts.sent[2].second["status"])
    }
}
