// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.testing.StatusSamples

class ElsewhereTest {
    @Test
    fun `a post read from another server keeps ids of its own, and every handle carries the domain`() {
        val read = StatusSamples.boost.elsewhere("other.example")
        assertEquals("remote:other.example:20", read.id)
        assertEquals("remote:other.example:10", read.reblog?.id)
        assertEquals("bob@other.social", read.account.acct)
        assertEquals("remote:bob@other.social", read.account.id)
        assertEquals("alice@other.example", read.reblog?.account?.acct)
    }

    @Test
    fun `its poll and the post it answers keep that server's ids too, and no quote is taken for ours`() {
        val read = StatusSamples.poll.copy(inReplyToId = "5", quoteId = "6").elsewhere("other.example")
        assertEquals("remote:other.example:" + StatusSamples.poll.poll?.id, read.poll?.id)
        assertEquals("remote:other.example:5", read.inReplyToId)
        assertEquals(null, read.quoteId)
    }
}
