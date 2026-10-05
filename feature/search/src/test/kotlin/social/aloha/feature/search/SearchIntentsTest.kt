// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.feature.search.SearchIntent.Accounts
import social.aloha.feature.search.SearchIntent.OpenUrl
import social.aloha.feature.search.SearchIntent.Person
import social.aloha.feature.search.SearchIntent.Posts
import social.aloha.feature.search.SearchIntent.Tag

class SearchIntentsTest {
    @Test
    fun `what is typed reads as an address, a person, a hashtag or words`() {
        assertEquals(listOf(OpenUrl("https://x.test/@a/1")), intentsFor(" https://x.test/@a/1 "))
        assertEquals(listOf(Person("@kai@surf.example"), Accounts("kai@surf.example")), intentsFor("kai@surf.example"))
        assertEquals(listOf(Person("@kai"), Accounts("@kai")), intentsFor("@kai"))
        assertEquals(listOf(Tag("surf"), Posts("#surf")), intentsFor("#surf"))
        assertEquals(listOf(Tag("surf"), Posts("surf"), Accounts("surf")), intentsFor("surf"))
        assertEquals(listOf(Posts("north shore"), Accounts("north shore")), intentsFor("north shore"))
        assertEquals(emptyList<SearchIntent>(), intentsFor("  "))
    }
}
