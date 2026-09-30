// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import social.aloha.core.model.OutboxState

/**
 * A post not yet on its server: a draft being written, or one waiting to be sent. The post itself is
 * [content], as the data layer serialises it; its files are the app's own copies, named in there.
 *
 * @property state one of [OutboxState]'s names.
 * @property error what the server said when it refused the post, for the writer to fix.
 */
@Entity(tableName = "outbox", indices = [Index("accountId", "state")])
public data class OutboxEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val state: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String? = null,
)

@Dao
public interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE accountId = :accountId ORDER BY updatedAt DESC")
    public fun observe(accountId: String): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE id = :id")
    public suspend fun get(id: String): OutboxEntity?

    @Upsert
    public suspend fun upsert(entry: OutboxEntity)

    @Query("DELETE FROM outbox WHERE id = :id")
    public suspend fun delete(id: String)

    /** Deletes [id] unless it is going out; how many rows went, 0 or 1. */
    @Query("DELETE FROM outbox WHERE id = :id AND state != 'Sending'")
    public suspend fun deleteUnlessSending(id: String): Int

    @Query("SELECT * FROM outbox WHERE accountId = :accountId")
    public suspend fun forAccount(accountId: String): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE accountId = :accountId")
    public suspend fun deleteAccount(accountId: String)

    @Query("SELECT content FROM outbox")
    public suspend fun contents(): List<String>

    @Query("UPDATE outbox SET state = :state, error = :error, updatedAt = :now WHERE id = :id")
    public suspend fun setState(id: String, state: String, error: String?, now: Long)

    @Query("UPDATE outbox SET content = :content, updatedAt = :now WHERE id = :id")
    public suspend fun setContent(id: String, content: String, now: Long)

    /** Takes [id] back for editing unless it is going out; how many rows it changed, 0 or 1. */
    @Query("UPDATE outbox SET state = 'Draft', error = NULL, updatedAt = :now WHERE id = :id AND state != 'Sending'")
    public suspend fun reopen(id: String, now: Long): Int

    /** Moves every post of [accountId] in state [from] to [to]. */
    @Query("UPDATE outbox SET state = :to, updatedAt = :now WHERE accountId = :accountId AND state = :from")
    public suspend fun moveAll(accountId: String, from: String, to: String, now: Long)

    @Query("SELECT * FROM outbox WHERE accountId = :accountId AND state = 'Queued' ORDER BY createdAt, rowid LIMIT 1")
    public suspend fun oldestQueued(accountId: String): OutboxEntity?

    /** The oldest queued post of [accountId], marked as going out, so no edit can race the sending. */
    @Transaction
    public suspend fun claim(accountId: String, now: Long): OutboxEntity? {
        val next = oldestQueued(accountId) ?: return null
        setState(next.id, OutboxState.Sending.name, null, now)
        return next.copy(state = OutboxState.Sending.name)
    }
}

/**
 * `outbox.db`: the posts not yet out, durable like the accounts but a file of its own, so that it
 * stays on the device: drafts and direct messages never go into a backup or a device transfer.
 */
@Database(entities = [OutboxEntity::class], version = 1, exportSchema = true)
public abstract class OutboxDatabase : RoomDatabase() {
    public abstract fun outboxDao(): OutboxDao

    public companion object {
        public const val FILE_NAME: String = "outbox.db"
    }
}
