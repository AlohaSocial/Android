// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import java.time.Instant
import social.aloha.core.model.Account
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.Card
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Mention
import social.aloha.core.model.Poll
import social.aloha.core.model.PollOption
import social.aloha.core.model.QuotedStatus
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility

/** Statuses for the row tests, dated against [NOW]. */
internal object StatusSamples {
    val NOW: Instant = Instant.parse("2026-09-29T12:00:00Z")
    val alice = Account("1", "alice", "alice", displayName = "Alice Example")
    val bob = Account("2", "bob", "bob@other.social", displayName = "Bob")
    private const val BLURHASH = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

    private const val BEACH =
        "<p>Aloha from the <a href=\"https://cloud.example/tags/beach\" class=\"hashtag\">#beach</a>!</p>"

    fun post(content: String = BEACH) = Status(
        "10",
        alice,
        url = "https://cloud.example/@alice/10",
        createdAt = NOW.minusSeconds(300),
        content = content,
        repliesCount = 2,
        reblogsCount = 5,
        favouritesCount = 12,
    )

    fun image(id: String, alt: String? = "A sunny beach") = MediaAttachment(
        id,
        AttachmentKind.Image,
        url = "https://cloud.example/m/$id.jpg",
        previewUrl = "https://cloud.example/m/$id-small.jpg",
        description = alt,
        blurhash = BLURHASH,
    )

    val boost = Status("20", bob, createdAt = NOW.minusSeconds(60), reblog = post())

    val reply = post().copy(
        id = "11",
        inReplyToId = "9",
        inReplyToAccountId = "2",
        mentions = listOf(Mention("2", "bob", "bob@other.social")),
    )

    val selfReply = post().copy(id = "12", inReplyToId = "10", inReplyToAccountId = "1")

    val spoiler = post().copy(id = "13", spoilerText = "Spoilers for the finale")

    val sensitive = post().copy(id = "14", sensitive = true, mediaAttachments = listOf(image("a")))

    val gallery = post().copy(
        id = "15",
        mediaAttachments = listOf(image("a"), image("b", alt = null), image("c"), image("d")),
    )

    val poll = post().copy(
        id = "16",
        content = "<p>Where next?</p>",
        poll = Poll(
            "p1",
            expiresAt = NOW.plusSeconds(86_400),
            options = listOf(PollOption("Maui", 4), PollOption("Kauai", 6)),
            votesCount = 10,
            votersCount = 10,
        ),
    )

    val pollResults = poll.copy(id = "17", poll = poll.poll!!.copy(voted = true, ownVotes = listOf(1)))

    val linked = post().copy(
        id = "18",
        content = "<p>Worth reading</p>",
        card = Card(
            url = "https://news.example/story",
            title = "A long story about the ocean",
            description = "How the tides work",
            providerName = "News Example",
        ),
    )

    val quoting = post().copy(
        id = "19",
        content = "<p>So true</p>",
        quote = QuotedStatus(
            "accepted",
            Status("30", bob, createdAt = NOW.minusSeconds(7200), content = "<p>The sea is big.</p>"),
        ),
    )

    val direct = post().copy(
        id = "21",
        visibility = Visibility.Direct,
        editedAt = NOW.minusSeconds(100),
        favourited = true,
        bookmarked = true,
        reblogged = true,
    )
}
