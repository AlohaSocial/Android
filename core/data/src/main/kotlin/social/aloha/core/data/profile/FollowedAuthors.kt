// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.Relationship
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.AccountEndpoints

/**
 * Whether the reader follows the authors they come across, asked for those on screen a batch at a
 * time rather than by reading through everyone they follow, and learnt again as they follow or
 * unfollow in the app.
 *
 * ponytail: held for the process only, so a follow made elsewhere shows from the next launch; kept in
 * the cache database if that turns out to matter.
 */
@Singleton
public class FollowedAuthors @Inject constructor(private val clients: ClientFactory) {
    // reader -> author -> whether the reader follows them
    private val known = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())
    private val asking = ConcurrentHashMap.newKeySet<String>()

    // reader/author -> when they may be asked about again, after a failed ask, so a timeline that keeps
    // changing does not ask a refusing server each time it does
    private val waiting = ConcurrentHashMap<String, Long>()

    public fun observe(readerId: String): Flow<Map<String, Boolean>> =
        known.map { it[readerId].orEmpty() }.distinctUntilChanged()

    /**
     * Asks about the [authors] not known or being asked about already. One the server leaves out of its
     * answer is taken as not followed; one whose ask fails is asked again after a while.
     */
    public suspend fun ask(reader: SignedInAccount, authors: Collection<String>) {
        val have = known.value[reader.id].orEmpty()
        val now = System.nanoTime()
        val unknown = authors.filter {
            val key = reader.id + "/" + it
            it !in have && (waiting[key] ?: Long.MIN_VALUE) <= now && asking.add(key)
        }
        unknown.chunked(BATCH).forEach { batch -> askBatch(reader, batch) }
    }

    private suspend fun askBatch(reader: SignedInAccount, batch: List<String>) {
        // released however this ends, a screen gone before the answer included
        try {
            when (val answer = clients.answer(reader, AccountEndpoints.relationships(batch))) {
                is Answer.Got -> answered(reader.id, batch, answer.value)
                is Answer.Missed -> later(reader.id, batch)
            }
        } finally {
            batch.forEach { asking.remove(reader.id + "/" + it) }
        }
    }

    private fun later(readerId: String, batch: List<String>) {
        val again = System.nanoTime() + RETRY_NANOS
        batch.forEach { waiting["$readerId/$it"] = again }
    }

    // those the server left out of its answer are not followed, as far as it will say
    private fun answered(readerId: String, batch: List<String>, relationships: List<Relationship>) {
        relationships.forEach { learn(readerId, it) }
        val told = relationships.mapTo(HashSet()) { it.id }
        batch.filterNot { it in told }.forEach { learn(readerId, Relationship(it)) }
    }

    /** What a relationship the server just answered with says. */
    public fun learn(readerId: String, relationship: Relationship) {
        // a follow request still waiting is no follow yet
        known.update { all ->
            all + (readerId to (all[readerId].orEmpty() + (relationship.id to relationship.following)))
        }
    }

    private companion object {
        // well within what Mastodon's relationships route reads at once
        const val BATCH = 40
        val RETRY_NANOS = TimeUnit.SECONDS.toNanos(30)
    }
}
