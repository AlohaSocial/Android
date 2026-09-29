// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import social.aloha.core.database.CachedStatusEntity
import social.aloha.core.database.StatusDao
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.ContentClassifier
import social.aloha.core.model.Status

/**
 * One status, one truth: every status an account has seen is stored once, and every timeline points
 * at that copy, so an edit, a favourite or a deletion shows up everywhere it appears.
 */
@Singleton
public class StatusRepository @Inject constructor(private val dao: StatusDao, private val clock: Clock) {
    private val decoded = DecodedStatuses()

    public fun observe(accountId: String, statusId: String): Flow<Status?> = dao.observe(accountId, statusId).map {
        it?.let { entity -> decoded.of(accountId, entity.serverId, entity.payloadJson, entity.cachedAt) }
    }

    public suspend fun get(accountId: String, statusId: String): Status? =
        dao.get(accountId, statusId)?.let { decoded.of(accountId, it.serverId, it.payloadJson, it.cachedAt) }

    /**
     * Replaces the stored copy, which is what an action the server confirmed or refused does. A boost
     * carries a copy of what it boosts, so every stored boost of [status] is rewritten with it: a
     * favourite shows on the post and on each boost of it alike.
     */
    public suspend fun save(accountId: String, status: Status) {
        val boosts = dao.boostsOf(accountId, status.id).mapNotNull { boost ->
            decoded.of(accountId, boost.serverId, boost.payloadJson, boost.cachedAt)?.copy(reblog = status)
        }
        dao.upsertAll(listOf(entity(accountId, status)) + boosts.map { entity(accountId, it) })
    }

    /** A deletion, or a 404 on refetch: the status goes, and from every timeline. */
    public suspend fun delete(accountId: String, statusId: String) {
        dao.delete(accountId, statusId)
    }

    /** Everything [authorId] posted or was boosted in, gone at once: what blocking or muting them means. */
    public suspend fun removeAuthor(accountId: String, authorId: String) {
        dao.deleteByAuthor(accountId, authorId)
    }

    internal fun entity(accountId: String, status: Status): CachedStatusEntity = CachedStatusEntity(
        accountId = accountId,
        serverId = status.id,
        payloadJson = json.encodeToString(Status.serializer(), status),
        cachedAt = clock.millis(),
        contentKind = ContentClassifier.classify(status).name,
        plainText = StatusHtmlParser.plainText(status.displayed.content),
        authorId = status.account.id,
        boostedAuthorId = status.reblog?.account?.id,
        reblogOfId = status.reblog?.id,
    )

    internal fun decode(accountId: String, statusId: String, payloadJson: String, cachedAt: Long): Status? =
        decoded.of(accountId, statusId, payloadJson, cachedAt)

    internal companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}

/**
 * Decoded statuses by id and write time, so a re-emitted timeline decodes only the rows that changed
 * rather than every payload on the screen. The least recently used entry goes once the cache is full.
 */
private class DecodedStatuses(private val capacity: Int = CAPACITY) {
    /** A decode with the payload it came from: two saves within one millisecond differ in payload. */
    private class Decoded(val payloadJson: String, val status: Status)

    private val entries = object : LinkedHashMap<String, Decoded>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Decoded>?): Boolean = size > capacity
    }

    fun of(accountId: String, statusId: String, payloadJson: String, cachedAt: Long): Status? {
        val key = "$accountId|$statusId|$cachedAt"
        synchronized(entries) { entries[key] }?.takeIf { it.payloadJson == payloadJson }?.let { return it.status }
        return decode(payloadJson)?.also { synchronized(entries) { entries[key] = Decoded(payloadJson, it) } }
    }

    // a payload written by an older build that no longer decodes is a cache miss, not a crash
    private fun decode(payloadJson: String): Status? = try {
        StatusRepository.json.decodeFromString(Status.serializer(), payloadJson)
    } catch (_: SerializationException) {
        null
    }

    private companion object {
        const val CAPACITY = 1_000
        const val LOAD_FACTOR = 0.75f
    }
}
