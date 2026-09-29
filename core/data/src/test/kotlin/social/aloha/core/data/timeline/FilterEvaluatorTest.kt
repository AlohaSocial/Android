// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import social.aloha.core.data.timeline.FilterEvaluator.Decision
import social.aloha.core.model.Account
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.model.FilterKeyword
import social.aloha.core.model.FilterResult
import social.aloha.core.model.FilterStatus
import social.aloha.core.model.Status

class FilterEvaluatorTest {
    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val alice = Account("1", "alice", "alice")

    private fun status(id: String = "1") = Status(id, alice)

    private fun filter(
        keyword: String,
        action: FilterAction = FilterAction.Hide,
        wholeWord: Boolean = false,
        contexts: List<FilterContext> = listOf(FilterContext.Home),
        expires: Instant? = null,
        id: String = "f1",
    ) = Filter(id, "Test $id", contexts, expires, action, listOf(FilterKeyword("k1", keyword, wholeWord)))

    private fun decide(
        text: String,
        vararg filters: Filter,
        context: FilterContext = FilterContext.Home,
        status: Status = status(),
    ) = FilterEvaluator(filters.toList(), context, now).decision(status, text)

    @Test
    fun `a hide filter removes the row`() {
        assertEquals(Decision.Hide, decide("talking about spoilers here", filter("spoilers")))
    }

    @Test
    fun `a warn filter collapses behind its title`() {
        assertEquals(Decision.Warn(listOf("Test f1")), decide("election news", filter("election", FilterAction.Warn)))
    }

    @Test
    fun `a filter only applies in the contexts it names`() {
        val onlyHome = filter("spoilers")
        assertEquals(Decision.Hide, decide("spoilers", onlyHome))
        assertEquals(Decision.Show, decide("spoilers", onlyHome, context = FilterContext.Public))
    }

    /** Why filters apply when rows are drawn: one that expires has to stop hiding without a refetch. */
    @Test
    fun `an expired filter stops applying immediately`() {
        assertEquals(Decision.Show, decide("spoilers", filter("spoilers", expires = now.minusSeconds(60))))
    }

    @Test
    fun `whole-word matching does not fire on a substring`() {
        val art = filter("art", wholeWord = true)
        assertEquals(Decision.Show, decide("a partial match", art))
        assertEquals(Decision.Hide, decide("some art here", art))
        assertEquals(Decision.Hide, decide("Art.", art))
    }

    /** The Apple app compared single tokens, so a phrase could never match whole-word. */
    @Test
    fun `a whole-word phrase matches as a phrase`() {
        val phrase = filter("new york", wholeWord = true)
        assertEquals(Decision.Hide, decide("flights to New York today", phrase))
        assertEquals(Decision.Show, decide("new yorkers", phrase))
    }

    @Test
    fun `hiding wins over warning, whatever order the filters come in`() {
        val hide = filter("spoilers")
        val warn = filter("election", FilterAction.Warn, id = "f2")
        assertEquals(Decision.Hide, decide("spoilers and election", hide, warn))
        assertEquals(Decision.Hide, decide("spoilers and election", warn, hide))
    }

    @Test
    fun `a boost is judged by what it boosts`() {
        val boost = Status("boost", Account("2", "bob", "bob"), reblog = status("inner"))
        val byId =
            Filter(
                "f1",
                "Hidden post",
                listOf(FilterContext.Home),
                filterAction = FilterAction.Hide,
                statuses = listOf(FilterStatus("s1", "inner")),
            )
        assertEquals(Decision.Hide, decide("", byId, status = boost))
    }

    @Test
    fun `an empty keyword never matches everything`() {
        assertEquals(Decision.Show, decide("anything", filter("")))
        assertEquals(Decision.Show, decide("anything", filter("  ")))
    }

    @Test
    fun `a keyword is literal text, not a pattern`() {
        assertEquals(Decision.Show, decide("abc", filter("a.c")))
        assertEquals(Decision.Hide, decide("costs 5$ (roughly)", filter("5$ (")))
    }

    @Test
    fun `a match the server reported is honoured in its context`() {
        val serverFilter = Filter("s", "Server side", listOf(FilterContext.Public), filterAction = FilterAction.Warn)
        val flagged = status().copy(filtered = listOf(FilterResult(serverFilter, keywordMatches = listOf("x"))))
        assertEquals(
            Decision.Warn(listOf("Server side")),
            decide("nothing to match", status = flagged, context = FilterContext.Public),
        )
        assertEquals(Decision.Show, decide("nothing to match", status = flagged, context = FilterContext.Home))
    }
}
