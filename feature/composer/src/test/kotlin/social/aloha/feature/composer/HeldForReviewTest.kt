// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.compose.DraftSegment
import social.aloha.core.model.OutboxState
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.sync.R as SyncR

/** Posts a review rule holds, as Social answers them: out for review, never a failure to retry. */
@RunWith(RobolectricTestRunner::class)
internal class HeldForReviewTest : ComposerTestSetup() {
    @Test
    fun `a post held for review is out, its draft gone, and nothing is offered again`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Hello, reef")
        val reader = accounts.all().single().id
        withTimeout(10.seconds) { outbox.observe(reader).first { it.isNotEmpty() } }
        script += HELD
        viewModel.onPost()
        val state = viewModel.await { it.done }
        assertEquals(PostFailure.Held(rest = null, reason = "first_post"), state.held)
        assertNull(state.failure)
        assertEquals(emptyList<Any>(), withTimeout(10.seconds) { outbox.observe(reader).first { it.isEmpty() } })
    }

    @Test
    fun `a thread stops at the segment held for review and keeps the rest as a draft`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "one")
        viewModel.onSegments()
        viewModel.type(1, "two")
        viewModel.onSegments()
        viewModel.type(2, "three")
        viewModel.await { it.remaining.size == 3 }
        script += listOf(200, HELD)
        viewModel.onPost()
        val state = viewModel.await { it.done }
        assertEquals(1, state.held?.kept)
        assertEquals(listOf("one", "two"), posts.sent.map { it.second["status"] })
        val reader = accounts.all().single().id
        val draft = withTimeout(10.seconds) {
            outbox.observe(reader).first { list ->
                list.any {
                    it.post.segments.map(DraftSegment::text) ==
                        listOf("three")
                }
            }
        }.single()
        assertEquals(OutboxState.Draft, draft.state)
        assertNull(draft.post.replyToId)
        assertNull(draft.post.segments.single().key)
    }

    @Test
    fun `a post written again and held for review deletes nothing and says the original stays`() = runBlocking {
        val viewModel = open(key = { ComposerKey(it, redraftId = "gone") })
        viewModel.await { it.ready && it.attachments.first().isNotEmpty() && it.canPost }
        script += HELD
        viewModel.onPost()
        val held = checkNotNull(viewModel.await { it.done }.held)
        assertTrue(held.replaces)
        assertEquals(emptyList<String>(), posts.deleted)
        val words = ApplicationProvider.getApplicationContext<Context>().resources
        val original = words.getString(SyncR.string.held_keeps_original, "").trim()
        assertTrue(held.toast(words).contains(original))
        assertFalse(held.copy(replaces = false).toast(words).contains(original))
    }

    @Test
    fun `a scheduled post held for review is out like one posted now`() = runBlocking {
        val viewModel = open()
        viewModel.await { it.ready }
        viewModel.type(0, "Later")
        viewModel.scheduledAt.value = Instant.parse("2030-01-01T09:00:00Z")
        viewModel.await { it.scheduledAt != null && it.canPost }
        script += HELD
        viewModel.onPost()
        val state = viewModel.await { it.done }
        assertEquals(0, state.held?.kept)
        assertNull(state.failure)
    }
}
