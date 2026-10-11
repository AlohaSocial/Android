// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.model.Account
import social.aloha.core.model.Mention
import social.aloha.core.model.Status

class MergedRowsTest {
    private val author = Account("9", "bob", "bob", displayName = "Bob")

    @Test
    fun `the people a post names on another account's server are named in full, under that server's name`() {
        val post = Status(
            id = "1",
            account = author,
            mentions = listOf(Mention("12", "carol", "carol"), Mention("13", "dan", "dan@third.social")),
        )
        val boost = Status(id = "2", account = author, reblog = post)

        val named = checkNotNull(boost.namingFrom("two.example").reblog).mentions

        assertEquals(listOf("carol@two.example", "dan@third.social"), named.map { it.acct })
        assertEquals(listOf("remote:carol@two.example", "remote:dan@third.social"), named.map { it.id })
    }
}
