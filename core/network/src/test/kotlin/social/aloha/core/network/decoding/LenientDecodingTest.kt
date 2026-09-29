// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.decoding

import java.time.Instant
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.network.AlohaJson
import social.aloha.core.network.dto.CustomEmojiDto
import social.aloha.core.network.dto.toDomain

class LenientDecodingTest {
    @Serializable
    private data class Probe(
        @Serializable(with = FlexibleIdSerializer::class) val id: String,
        @Serializable(with = OptionalIdSerializer::class) val parent: String? = null,
        @Serializable(with = LenientUrlSerializer::class) val avatar: String? = null,
        @Serializable(with = LenientBoolSerializer::class) val locked: Boolean = false,
        @Serializable(with = LenientIntSerializer::class) val count: Int = 0,
        @Serializable(with = LenientInstantSerializer::class) val at: Instant? = null,
        @Serializable(with = LossyListSerializer::class) val emojis: List<CustomEmojiDto> = emptyList(),
    )

    private fun decode(json: String): Probe = AlohaJson.decodeFromString(Probe.serializer(), json)

    @Test
    fun `a numeric id beyond 2^53 keeps every digit`() {
        assertEquals("1790000000000000123", decode("""{"id": 1790000000000000123}""").id)
    }

    @Test
    fun `an empty or null optional id is absent`() {
        assertNull(decode("""{"id": "1", "parent": ""}""").parent)
        assertNull(decode("""{"id": "1", "parent": null}""").parent)
        assertEquals("42", decode("""{"id": "1", "parent": 42}""").parent)
    }

    @Test
    fun `an empty avatar is no avatar`() {
        assertNull(decode("""{"id": "1", "avatar": ""}""").avatar)
        assertNull(decode("""{"id": "1", "avatar": null}""").avatar)
        assertNull(decode("""{"id": "1", "avatar": "  "}""").avatar)
    }

    @Test
    fun `booleans and counts accept the shapes forks send`() {
        assertTrue(decode("""{"id": "1", "locked": 1}""").locked)
        assertTrue(decode("""{"id": "1", "locked": "true"}""").locked)
        assertFalse(decode("""{"id": "1", "locked": "nope"}""").locked)
        assertEquals(12, decode("""{"id": "1", "count": "12"}""").count)
        assertEquals(0, decode("""{"id": "1", "count": null}""").count)
    }

    @Test
    fun `dates in every form a server sends`() {
        val expected = Instant.parse("2026-09-28T10:00:00Z")
        assertEquals(expected, decode("""{"id": "1", "at": "2026-09-28T10:00:00.000Z"}""").at)
        assertEquals(expected, decode("""{"id": "1", "at": "2026-09-28T10:00:00Z"}""").at)
        assertEquals(expected, decode("""{"id": "1", "at": "2026-09-28T12:00:00+02:00"}""").at)
        assertEquals(Instant.parse("2026-09-28T00:00:00Z"), decode("""{"id": "1", "at": "2026-09-28"}""").at)
        assertEquals(Instant.ofEpochSecond(1790000000), decode("""{"id": "1", "at": "1790000000"}""").at)
        assertNull(decode("""{"id": "1", "at": "yesterday"}""").at)
    }

    @Test
    fun `a malformed element is dropped and recorded, the rest survive`() {
        val (probe, failures) = DecodingFailures.collect {
            decode("""{"id": "1", "emojis": [{"shortcode": "a"}, {"url": "x"}, {"shortcode": "b"}]}""")
        }
        assertEquals(listOf("a", "b"), probe.emojis.map { it.toDomain().shortcode })
        assertEquals(listOf(1), failures.map { it.index })
    }

    @Test
    fun `an emoji without visible_in_picker is visible`() {
        val emoji = AlohaJson.decodeFromString(CustomEmojiDto.serializer(), """{"shortcode": "x"}""").toDomain()
        assertTrue(emoji.visibleInPicker)
    }
}
