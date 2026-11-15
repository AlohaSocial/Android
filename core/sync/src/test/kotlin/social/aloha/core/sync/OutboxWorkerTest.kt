// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import org.robolectric.Shadows.shadowOf
import social.aloha.core.data.compose.DraftMedia
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.DraftSegment
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.model.OutboxState
import social.aloha.core.model.SignedInAccount
import social.aloha.core.testing.NumberedTimeline
import social.aloha.core.testing.SignedInFixture

/** What [Server] answers for a post a review rule holds: a 422 with `held_for_review`, as Social does. */
private const val HELD = -422

private const val HELD_BODY = """{"error":"This post is waiting for a moderator to look at it.",""" +
    """"held_for_review":true,"reason":"links",""" +
    """"held_post":{"id":"9","reason":"links","text":"Two","media_count":0}}"""

/**
 * A server that answers `POST /statuses` with the next code of [posts] (a status on 200, a hold on
 * [HELD]), media by [media], and remembers what was asked, with the idempotency key of each post.
 */
private class Server(private val posts: MutableList<Int>, private val media: MutableList<Int>) : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()
    val keys = CopyOnWriteArrayList<String?>()
    private val template = JsonObject(NumberedTimeline.homeTemplate() + ("reblog" to JsonNull))
    private var next = 0

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path"
        return when {
            path.endsWith("/statuses") -> {
                keys += request.headers["Idempotency-Key"]
                when (val code = posts.removeFirstOrNull() ?: 200) {
                    200 -> json(200, JsonObject(template + ("id" to JsonPrimitive("s${++next}"))).toString())
                    422 -> json(422, """{"error":"Text character limit of 500 exceeded"}""")
                    HELD -> json(422, HELD_BODY)
                    else -> json(code, "{}")
                }
            }

            "/media" in path -> when (val code = media.removeFirstOrNull() ?: 200) {
                200 -> json(200, """{"id":"new","type":"image","url":"https://x.test/new.jpg"}""")
                else -> json(code, "{}")
            }

            else -> json(404, "{}")
        }
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
}

@RunWith(RobolectricTestRunner::class)
class OutboxWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val posts = mutableListOf<Int>()
    private val media = mutableListOf<Int>()
    private val answers = Server(posts, media)
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private suspend fun run(account: SignedInAccount, attempt: Int = 0): ListenableWorker.Result =
        TestListenableWorkerBuilder<OutboxWorker>(context)
            .setInputData(workDataOf(OutboxWorker.ACCOUNT to account.id))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = OutboxWorker(
                        appContext,
                        workerParameters,
                        fixture.accounts,
                        MediaRepository(fixture.clients),
                        fixture.outbox,
                        fixture.sender,
                    )
                },
            )
            .build()
            .doWork()

    private fun post(vararg texts: String, media: List<DraftMedia> = emptyList()) = DraftPost(
        segments = texts.mapIndexed { index, text ->
            DraftSegment(text, if (index == 0) media else emptyList(), key = "key-$index")
        },
    )

    private suspend fun left(account: SignedInAccount) = fixture.outbox.observe(account.id).first()

    @Test
    fun `media the server let go of is uploaded again, and the post goes out with the key it was queued with`() =
        runBlocking {
            val account = fixture.signIn(server.url("/"))
            val file = File(context.filesDir, "wave.jpg").apply { writeBytes(ByteArray(512)) }
            val wave = DraftMedia("wave.jpg", "image/jpeg", file.absolutePath, mediaId = "old", description = "A wave")
            fixture.outbox.queue("p", account.id, post("Look", media = listOf(wave)))
            // the old id is gone: the description cannot be set on it
            media += listOf(404, 200)
            assertTrue(run(account) is ListenableWorker.Result.Success)
            assertEquals(
                listOf("PUT /api/v1/media/old", "POST /api/v2/media", "POST /api/v1/statuses"),
                answers.asked,
            )
            assertEquals(listOf("key-0"), answers.keys)
            assertEquals(emptyList<Any>(), left(account))
            assertTrue("the app's copy goes with the post", !file.exists())
        }

    @Test
    fun `a thread cut off half way keeps what went out and carries on from there`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        fixture.outbox.queue("t", account.id, post("One", "Two"))
        posts += listOf(200, 500)
        assertTrue(run(account) is ListenableWorker.Result.Retry)
        val waiting = left(account).single()
        assertEquals(OutboxState.Queued, waiting.state)
        assertEquals(listOf("s1"), waiting.post.postedIds)
        assertTrue(run(account) is ListenableWorker.Result.Success)
        // the second segment went again under its own key, answering the first
        assertEquals(listOf("key-0", "key-1", "key-1"), answers.keys)
    }

    @Test
    fun `a refused post is set aside with the reason, and the queue goes on`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        fixture.outbox.queue("long", account.id, post("Too long"))
        fixture.outbox.queue("short", account.id, post("Short"))
        posts += listOf(422, 200)
        assertTrue(run(account) is ListenableWorker.Result.Success)
        val refused = left(account).single()
        assertEquals("long", refused.id)
        assertEquals(OutboxState.Failed, refused.state)
        assertEquals("Text character limit of 500 exceeded", refused.error)
    }

    @Test
    fun `a post held for review counts as sent, the rest of its thread is a draft, and the queue goes on`() =
        runBlocking {
            val account = fixture.signIn(server.url("/"))
            val file = File(context.filesDir, "three.jpg").apply { writeBytes(ByteArray(16)) }
            val three = DraftMedia("three.jpg", "image/jpeg", file.absolutePath, mediaId = "m3")
            val thread = post("One", "Two", "Three").let {
                it.copy(segments = it.segments.toMutableList().apply { this[2] = this[2].copy(media = listOf(three)) })
            }
            fixture.outbox.queue("thread", account.id, thread)
            fixture.outbox.queue("next", account.id, post("Next"))
            posts += listOf(200, HELD, 200)
            assertTrue(run(account) is ListenableWorker.Result.Success)
            // the third segment of the thread never went, and waits as a draft with its picture; the next post went
            val rest = left(account).single()
            assertEquals("thread", rest.id)
            assertEquals(OutboxState.Draft, rest.state)
            assertEquals(listOf(DraftSegment("Three", listOf(three))), rest.post.segments)
            assertTrue("the draft keeps its picture", file.exists())
            assertEquals(listOf("key-0", "key-1", "key-0"), answers.keys)
        }

    @Test
    fun `a single post held for review leaves nothing behind`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        fixture.outbox.queue("one", account.id, post("One"))
        posts += HELD
        assertTrue(run(account) is ListenableWorker.Result.Success)
        assertEquals(emptyList<Any>(), left(account))
        assertEquals(1, answers.keys.size)
    }

    @Test
    fun `a post written again and held for review deletes nothing, and the writer hears the original stays`() =
        runBlocking {
            shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            val account = fixture.signIn(server.url("/"))
            fixture.outbox.queue("again", account.id, post("Again").copy(replaces = "original"))
            posts += HELD
            assertTrue(run(account) is ListenableWorker.Result.Success)
            assertEquals(emptyList<String>(), answers.asked.filter { it.startsWith("DELETE") })
            val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
            val original = context.getString(R.string.held_keeps_original, "")
            assertTrue(shown.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains(original.trim()))
        }

    @Test
    fun `a known review rule is explained after the message, an unknown one is left out`() {
        val words = context.resources
        val links = context.getString(R.string.held_reason_links)
        val original = context.getString(R.string.held_keeps_original, "Held.")
        assertEquals("Held. $links", heldMessage(words, "Held.", "links", replaces = false))
        assertEquals("$original $links", heldMessage(words, "Held.", "links", replaces = true))
        assertEquals("Held.", heldMessage(words, "Held.", "spam_score", replaces = false))
        assertEquals("Held.", heldMessage(words, "Held.", null, replaces = false))
    }

    @Test
    fun `a revoked sign-in pauses the queue`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        fixture.outbox.queue("a", account.id, post("First"))
        fixture.outbox.queue("b", account.id, post("Second"))
        posts += 401
        assertTrue(run(account) is ListenableWorker.Result.Success)
        assertEquals(setOf(OutboxState.Paused), left(account).map { it.state }.toSet())
        assertEquals(1, answers.keys.size)
    }

    @Test
    fun `a post that never goes is set aside after a few tries, so the queue goes on`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        fixture.outbox.queue("stuck", account.id, post("Stuck"))
        posts += 500
        assertTrue(run(account, attempt = 8) is ListenableWorker.Result.Success)
        assertEquals(OutboxState.Failed, left(account).single().state)
    }

    @Test
    fun `a drain with nothing queued ends at once`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        assertTrue(run(account) is ListenableWorker.Result.Success)
        assertEquals(emptyList<String>(), answers.asked)
    }
}
