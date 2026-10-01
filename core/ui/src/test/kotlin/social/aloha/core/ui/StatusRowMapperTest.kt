// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.ui.graphics.Color
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Card
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.QuotedStatus
import social.aloha.core.testing.StatusSamples
import social.aloha.core.testing.StatusSamples.NOW
import social.aloha.core.ui.StatusRowUi.ContextLine

class StatusRowMapperTest {
    private val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))

    @Test
    fun `a boost is drawn as what it boosts, with who boosted it above`() {
        val row = mapper.map(StatusSamples.boost, viewerAccountId = null)
        assertEquals("20", row.rowId)
        assertEquals("10", row.statusId)
        assertEquals(ContextLine.BoostedBy("Bob"), row.context)
        assertEquals("Alice Example", row.author.plainName)
    }

    @Test
    fun `a reply names who it replies to, and a reply to oneself is a continued thread`() {
        assertEquals(ContextLine.ReplyingTo("@bob@other.social"), mapper.map(StatusSamples.reply, null).context)
        assertEquals(ContextLine.ContinuedThread, mapper.map(StatusSamples.selfReply, null).context)
        val unnamed = StatusSamples.reply.copy(mentions = emptyList())
        assertEquals(ContextLine.Replying, mapper.map(unnamed, null).context)
    }

    @Test
    fun `a thread asks for no context line at all`() {
        assertNull(mapper.map(StatusSamples.boost, null, showContext = false).context)
    }

    @Test
    fun `a pinned post says so, but a boost says who boosted first`() {
        assertEquals(ContextLine.Pinned, mapper.map(StatusSamples.post().copy(pinned = true), null).context)
        val pinnedBoost = StatusSamples.boost.copy(reblog = StatusSamples.post().copy(pinned = true))
        assertEquals(ContextLine.BoostedBy("Bob"), mapper.map(pinnedBoost, null).context)
    }

    @Test
    fun `a link card is left out next to media, which already shows what the post is about`() {
        val card = Card(url = "https://news.example/")
        assertEquals(card, mapper.map(StatusSamples.post().copy(card = card), null).card)
        assertNull(mapper.map(StatusSamples.sensitive.copy(card = card), null).card)
        assertNull(mapper.map(StatusSamples.post().copy(card = Card(url = null)), null).card)
    }

    @Test
    fun `the reader's own posts are told apart by the server account id`() {
        assertTrue(mapper.map(StatusSamples.post(), viewerAccountId = "1").isOwn)
        assertFalse(mapper.map(StatusSamples.post(), viewerAccountId = "2").isOwn)
        assertFalse(mapper.map(StatusSamples.boost, viewerAccountId = "2").isOwn)
    }

    @Test
    fun `a withdrawn quote says so instead of showing the post`() {
        val withdrawn = StatusSamples.post().copy(quote = QuotedStatus("revoked"))
        assertTrue(mapper.map(withdrawn, null).quoteWithdrawn)
        assertNull(mapper.map(withdrawn, null).quote)
        assertEquals("30", mapper.map(StatusSamples.quoting, null).quote?.statusId)
    }

    @Test
    fun `a display name keeps its custom emoji as a slot`() {
        val named = StatusSamples.post().copy(
            account = StatusSamples.alice.copy(
                displayName = "Alice :wave:",
                emojis = listOf(CustomEmoji("wave", "https://x/wave.png")),
            ),
        )
        val row = mapper.map(named, null)
        assertEquals("Alice :wave:", row.author.name.text)
        assertEquals(emojiSlot("wave"), row.author.name.getStringAnnotations(0, row.author.name.length).single().item)
    }

    @Test
    fun `a content warning is rendered apart from the body`() {
        val row = mapper.map(StatusSamples.spoiler, null)
        assertEquals("Spoilers for the finale", row.spoiler?.text)
        assertNull(mapper.map(StatusSamples.post(), null).spoiler)
    }
}

class PostAgeTest {
    @Test
    fun `a post dated ahead of the phone, or under a minute old, is now`() {
        assertEquals(PostAge.Now, PostAge.of(NOW.plusSeconds(600), NOW))
        assertEquals(PostAge.Now, PostAge.of(NOW.minusSeconds(59), NOW))
    }

    @Test
    fun `older posts count minutes, hours and days, and a week on show their date`() {
        assertEquals(PostAge.Ago(5, PostAge.Unit.Minutes), PostAge.of(NOW.minusSeconds(300), NOW))
        assertEquals(PostAge.Ago(2, PostAge.Unit.Hours), PostAge.of(NOW.minus(Duration.ofMinutes(150)), NOW))
        assertEquals(PostAge.Ago(6, PostAge.Unit.Days), PostAge.of(NOW.minus(Duration.ofDays(6)), NOW))
        assertEquals(PostAge.On(NOW.minus(Duration.ofDays(8))), PostAge.of(NOW.minus(Duration.ofDays(8)), NOW))
    }
}

class StatusChoicesTest {
    @Test
    fun `a single-choice poll holds one answer, a multiple-choice one toggles`() {
        assertEquals(listOf(2), toggled(listOf(0), 2, multiple = false))
        assertEquals(listOf(0, 2), toggled(listOf(2), 0, multiple = true))
        assertEquals(listOf(2), toggled(listOf(0, 2), 0, multiple = true))
    }

    @Test
    fun `the author's own menu has edit and delete, anyone else's has report`() {
        val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
        val own = menuItems(mapper.map(StatusSamples.post(), viewerAccountId = "1")).map { it.first }
        val theirs = menuItems(mapper.map(StatusSamples.post(), viewerAccountId = "2")).map { it.first }
        assertTrue(StatusMenuItem.Delete in own && StatusMenuItem.Report !in own)
        assertTrue(StatusMenuItem.Report in theirs && StatusMenuItem.Delete !in theirs)
        // an album takes only the author's own posts, and only ones with pictures
        assertTrue(StatusMenuItem.AddToAlbum !in own)
        val gallery = menuItems(mapper.map(StatusSamples.gallery, viewerAccountId = "1")).map { it.first }
        val theirGallery = menuItems(mapper.map(StatusSamples.gallery, viewerAccountId = "2")).map { it.first }
        assertTrue(StatusMenuItem.AddToAlbum in gallery && StatusMenuItem.AddToAlbum !in theirGallery)
    }
}
