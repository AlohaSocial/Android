// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import social.aloha.core.database.OutboxDao
import social.aloha.core.database.OutboxEntity
import social.aloha.core.model.OutboxState

/** A post in the outbox, as the composer and the drafts list see it. */
public data class OutboxEntry(
    val id: String,
    val accountId: String,
    val state: OutboxState,
    val post: DraftPost,
    val updatedAt: Instant,
    /** What the server said when it refused the post. */
    val error: String?,
)

/**
 * The posts not yet on their server: drafts, and posts waiting to be sent. Kept in `outbox.db`, so
 * they outlive the app, a restart and an update, and stay on the device, out of every backup; what is
 * lost with the cache is never a post. The files they attach are the app's copies in [UPLOADS].
 */
@Singleton
public class Outbox @Inject constructor(private val dao: OutboxDao, private val clock: Clock) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    public fun observe(accountId: String): Flow<List<OutboxEntry>> =
        dao.observe(accountId).map { rows -> rows.mapNotNull(::entry) }

    public suspend fun get(id: String): OutboxEntry? = dao.get(id)?.let(::entry)

    /** Keeps [post] as draft [id] of [accountId]; one being sent is left alone. */
    public suspend fun saveDraft(id: String, accountId: String, post: DraftPost) {
        val now = clock.millis()
        val existing = dao.get(id)
        if (existing?.state == OutboxState.Sending.name) return
        dao.upsert(
            OutboxEntity(
                id,
                accountId,
                OutboxState.Draft.name,
                json.encodeToString(DraftPost.serializer(), post),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** Takes [id] back for editing; false while it is going out, when nothing may change it. */
    public suspend fun reopen(id: String): Boolean = dao.get(id) == null || dao.reopen(id, clock.millis()) > 0

    /**
     * Forgets [id], and with [files] the app's copies of what it attached; false while it is going
     * out, when nothing may change it.
     */
    public suspend fun delete(id: String, files: Boolean): Boolean {
        val post = dao.get(id)?.let(::entry)?.post
        val deleted = dao.deleteUnlessSending(id) > 0
        if (deleted && files) post?.files?.forEach { it.delete() }
        return deleted
    }

    /** The post [id] is out: it goes, with the files it attached. */
    public suspend fun sent(id: String) {
        dao.get(id)?.let(::entry)?.post?.files?.forEach { it.delete() }
        dao.delete(id)
    }

    /** Forgets everything [accountId] had not sent, with the files it attached: it signed out. */
    public suspend fun forget(accountId: String) {
        dao.forAccount(accountId).mapNotNull(::entry).forEach { entry -> entry.post.files.forEach { it.delete() } }
        dao.deleteAccount(accountId)
    }

    /**
     * Deletes the files in [uploads] that no post in the outbox attaches and that have not changed
     * for [idle]: what a composer copied and then let go of without a draft, a refused file, an
     * interrupted conversion. A composer open now keeps its files, which are newer than that.
     */
    public suspend fun sweep(uploads: File, idle: Duration = SWEEP_IDLE) {
        val kept = dao.contents().mapNotNull { content ->
            runCatching { json.decodeFromString(DraftPost.serializer(), content) }.getOrNull()
        }.flatMapTo(HashSet()) { post -> post.files.map { it.absolutePath } }
        val cutoff = clock.millis() - idle.toMillis()
        uploads.listFiles().orEmpty()
            .filter { it.isFile && it.absolutePath !in kept && it.lastModified() < cutoff }
            .forEach { it.delete() }
    }

    public companion object {
        /** The directory in the app's files that holds the copies of what posts attach. */
        public const val UPLOADS: String = "uploads"

        private val SWEEP_IDLE: Duration = Duration.ofDays(1)
    }

    private fun entry(row: OutboxEntity): OutboxEntry? = runCatching {
        OutboxEntry(
            row.id,
            row.accountId,
            OutboxState.valueOf(row.state),
            json.decodeFromString(DraftPost.serializer(), row.content),
            Instant.ofEpochMilli(row.updatedAt),
            row.error,
        )
    }.getOrNull()
}
