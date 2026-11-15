// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.navigation.ComposerKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
internal class EditedMediaTest : ComposerTestSetup() {
    @Test
    fun `an edit of a post whose media lost their ids locks them and sends nothing of them`() = runBlocking {
        posts.pictureId = ""
        val viewModel = open(key = { ComposerKey(it, editId = "mine") }, version = "0.26.299")
        val state = viewModel.await { it.ready && it.attachments.first().isNotEmpty() }
        assertEquals(true, state.mediaLocked)
        assertEquals(true, state.mediaDamaged)
        assertEquals(0, viewModel.room)
        viewModel.attachments.describe(state.attachments.first().single().id, "New words", null)
        viewModel.type(0, "As I meant it")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        val edit = posts.edits.single()
        assertEquals("As I meant it", edit["status"]?.jsonPrimitive?.content)
        assertNull(edit["media_ids"])
        assertNull(edit["media_attributes"])
    }

    @Test
    fun `an edit that took off media which lost their ids still sends no list of them`() = runBlocking {
        posts.pictureId = ""
        val viewModel = open(key = { ComposerKey(it, editId = "mine") }, version = "0.26.299")
        val state = viewModel.await { it.ready && it.attachments.first().isNotEmpty() }
        viewModel.attachments.remove(state.attachments.first().single().id)
        Snapshot.sendApplyNotifications()
        viewModel.await { it.attachments.first().isEmpty() && it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertNull(posts.edits.single()["media_ids"])
    }

    @Test
    fun `an edit changes the media of a post whose ids it kept, on a server that changes them`() = runBlocking {
        val viewModel = open(key = { ComposerKey(it, editId = "mine") }, version = "0.26.299")
        val state = viewModel.await { it.ready && it.attachments.first().isNotEmpty() }
        assertEquals(false, state.mediaLocked)
        viewModel.attachments.remove(state.attachments.first().single().id)
        Snapshot.sendApplyNotifications()
        viewModel.await { it.attachments.first().isEmpty() && it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals(JsonArray(emptyList()), posts.edits.single()["media_ids"])
    }

    @Test
    fun `a redraft leaves out media that lost their ids, and says so`() = runBlocking {
        posts.pictureId = ""
        val viewModel = open(key = { ComposerKey(it, redraftId = "gone") }, version = "0.26.299")
        viewModel.await { it.ready }
        Snapshot.sendApplyNotifications()
        val state = viewModel.await { it.canPost }
        assertEquals(true, state.mediaLeftOut)
        assertEquals(emptyList<Attachment>(), state.attachments.first())
        viewModel.onPost()
        viewModel.await { it.done }
        assertNull(posts.sent.single().second["media_ids[]"])
    }
}
