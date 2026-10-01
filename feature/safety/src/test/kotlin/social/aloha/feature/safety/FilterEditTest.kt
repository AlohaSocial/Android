// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.model.FilterKeyword
import social.aloha.core.network.endpoints.KeywordDraft

class FilterEditTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private val filter = Filter(
        "7",
        title = "Spoilers",
        context = listOf(FilterContext.Home, FilterContext.Unknown),
        expiresAt = now.plusSeconds(3600),
        filterAction = FilterAction.Hide,
        keywords = listOf(FilterKeyword("1", "finale", wholeWord = true)),
    )

    @Test
    fun `an expiry kept as it is is not sent, so the server keeps it`() {
        val editing = filter.editing()
        assertEquals(Expiry.Keep(now.plusSeconds(3600)), editing.expiry)
        assertEquals(setOf(FilterContext.Home), editing.contexts)
        assertTrue(editing.draft().keepExpiry)
        assertNull(editing.draft().expiresInSeconds)
    }

    @Test
    fun `a filter that ran out stays run out when it is saved, unless another expiry is chosen`() {
        val editing = filter.copy(expiresAt = now.minusSeconds(1)).editing()
        assertEquals(Expiry.Keep(now.minusSeconds(1)), editing.expiry)
        assertTrue(editing.draft().keepExpiry)
        val forGood = editing.copy(expiry = Expiry.Never).draft()
        assertEquals(false, forGood.keepExpiry)
        assertNull(forGood.expiresInSeconds)
        assertEquals(3600L, editing.copy(expiry = Expiry.In(3600)).draft().expiresInSeconds)
    }

    @Test
    fun `a filter that blurs keeps blurring when it is edited`() {
        assertEquals(FilterAction.Blur, filter.copy(filterAction = FilterAction.Blur).editing().action)
        assertEquals(FilterAction.Warn, filter.copy(filterAction = FilterAction.Unknown).editing().action)
    }

    @Test
    fun `a keyword the server has is sent as removed, beside the ones added`() {
        val typed = filter.editing().copy(
            keywords = listOf(
                KeywordDraft("finale", id = "1", wholeWord = true, destroy = true),
                KeywordDraft("ending"),
            ),
        )
        assertEquals(listOf("ending"), typed.shownKeywords.map { it.keyword })
        assertEquals(listOf(true, false), typed.draft().keywords.map { it.destroy })
    }

    @Test
    fun `a filter needs a name and somewhere to apply`() {
        val editing = filter.editing()
        assertEquals(true, editing.canSave)
        assertEquals(false, editing.copy(title = " ").canSave)
        assertEquals(false, editing.copy(contexts = emptySet()).canSave)
    }
}
