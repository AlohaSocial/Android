// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal val alice = Account(id = "1", username = "alice", acct = "alice@example.test", displayName = "Alice")

internal fun attachment(
    type: AttachmentKind,
    id: String = "m1",
    duration: Double? = null,
    width: Int? = null,
    height: Int? = null,
): MediaAttachment = MediaAttachment(
    id = id,
    type = type,
    url = "https://example.test/$id",
    meta = if (duration != null || width != null) {
        MediaMeta(original = MediaDimensions(width = width, height = height, duration = duration))
    } else {
        null
    },
)

internal fun status(
    attachments: List<MediaAttachment> = emptyList(),
    tags: List<String> = emptyList(),
    card: Card? = null,
): Status = Status(
    id = "s1",
    account = alice,
    mediaAttachments = attachments,
    tags = tags.map {
        StatusTag(it)
    },
    card = card,
)

class ContentClassifierTest {
    @Test
    fun `text with no attachments and no card is text`() {
        assertEquals(ContentKind.Text, ContentClassifier.classify(status()))
    }

    @Test
    fun `a status carrying only a link card is news`() {
        val card = Card(url = "https://example.test/article", title = "Something")
        assertEquals(ContentKind.News, ContentClassifier.classify(status(card = card)))
    }

    @Test
    fun `images only is a photo post`() {
        val s = status(
            listOf(
                attachment(AttachmentKind.Image, "a", width = 1200, height = 800),
                attachment(AttachmentKind.Image, "b", width = 1200, height = 800),
            ),
        )
        assertEquals(ContentKind.Photo, ContentClassifier.classify(s))
    }

    @Test
    fun `audio only is audio`() {
        assertEquals(
            ContentKind.Audio,
            ContentClassifier.classify(status(listOf(attachment(AttachmentKind.Audio, duration = 1800.0)))),
        )
    }

    @Test
    fun `a single portrait clip under three minutes is a short`() {
        val s = status(listOf(attachment(AttachmentKind.Video, duration = 25.0, width = 1080, height = 1920)))
        assertEquals(ContentKind.Short, ContentClassifier.classify(s))
    }

    @Test
    fun `anything at or under sixty seconds is a short regardless of shape`() {
        val s = status(listOf(attachment(AttachmentKind.Video, duration = 45.0, width = 1920, height = 1080)))
        assertEquals(ContentKind.Short, ContentClassifier.classify(s))
    }

    @Test
    fun `a landscape clip between one and three minutes is a video`() {
        val s = status(listOf(attachment(AttachmentKind.Video, duration = 120.0, width = 1920, height = 1080)))
        assertEquals(ContentKind.Video, ContentClassifier.classify(s))
    }

    @Test
    fun `a long portrait video is still a video`() {
        val s = status(listOf(attachment(AttachmentKind.Video, duration = 2400.0, width = 1080, height = 1920)))
        assertEquals(ContentKind.Video, ContentClassifier.classify(s))
    }

    @Test
    fun `a shorts hashtag promotes a clip whose shape says otherwise`() {
        val s = status(listOf(attachment(AttachmentKind.Video, width = 1920, height = 1080)), tags = listOf("shorts"))
        assertEquals(ContentKind.Short, ContentClassifier.classify(s))
    }

    @Test
    fun `a hashtag cannot rescue a clip that is simply too long`() {
        val s =
            status(
                listOf(attachment(AttachmentKind.Video, duration = 600.0, width = 1080, height = 1920)),
                tags = listOf("loops"),
            )
        assertEquals(ContentKind.Video, ContentClassifier.classify(s))
    }

    @Test
    fun `missing meta defers the decision`() {
        // Nextcloud Social without ffmpeg sends empty meta for every video
        assertEquals(
            ContentKind.Undetermined,
            ContentClassifier.classify(status(listOf(attachment(AttachmentKind.Video)))),
        )
    }

    @Test
    fun `landscape without a duration is settled as video`() {
        val s = status(listOf(attachment(AttachmentKind.Video, width = 1920, height = 1080)))
        assertEquals(ContentKind.Video, ContentClassifier.classify(s))
    }

    @Test
    fun `reclassification settles a deferred decision once a player reports`() {
        val s = status(listOf(attachment(AttachmentKind.Video, id = "v")))
        assertEquals(
            ContentKind.Short,
            ContentClassifier.reclassify(s, "v", MediaDimensions(width = 1080, height = 1920, duration = 18.0)),
        )
        assertEquals(
            ContentKind.Video,
            ContentClassifier.reclassify(s, "v", MediaDimensions(width = 1920, height = 1080, duration = 900.0)),
        )
    }

    @Test
    fun `a boost is classified by what it boosts`() {
        val inner = status(listOf(attachment(AttachmentKind.Video, duration = 20.0, width = 1080, height = 1920)))
        val boost = Status(id = "boost", account = alice, reblog = inner)
        assertEquals(ContentKind.Short, ContentClassifier.classify(boost))
    }

    @Test
    fun `mixed video and images is a video post`() {
        val s = status(
            listOf(
                attachment(AttachmentKind.Image, "a", width = 100, height = 100),
                attachment(AttachmentKind.Video, "b", duration = 30.0, width = 1080, height = 1920),
            ),
        )
        assertEquals(ContentKind.Video, ContentClassifier.classify(s))
    }

    @Test
    fun `kinds route into the right modes`() {
        assertTrue(ContentKind.Short.belongs(FeedMode.Shorts))
        assertTrue(ContentKind.Short.belongs(FeedMode.Video))
        assertFalse(ContentKind.Video.belongs(FeedMode.Shorts))
        assertTrue(ContentKind.Photo.belongs(FeedMode.Photos))
        assertTrue(ContentKind.Text.belongs(FeedMode.Home))
        assertFalse(ContentKind.Text.belongs(FeedMode.Photos))
        // a clip the server did not describe is a video until a player says whether it is a short
        assertTrue(ContentKind.Undetermined.belongs(FeedMode.Video))
        assertFalse(ContentKind.Undetermined.belongs(FeedMode.Shorts))
    }

    @Test
    fun `layout aspect is square for several attachments and the attachment's own for one`() {
        assertEquals(MediaAttachment.DEFAULT_ASPECT, ContentClassifier.layoutAspect(emptyList()))
        assertEquals(
            1.0,
            ContentClassifier.layoutAspect(
                listOf(attachment(AttachmentKind.Image, "a"), attachment(AttachmentKind.Image, "b")),
            ),
        )
        assertEquals(
            0.5,
            ContentClassifier.layoutAspect(listOf(attachment(AttachmentKind.Image, width = 100, height = 200))),
        )
    }
}
