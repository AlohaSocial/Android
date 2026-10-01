// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.database

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One status an account has seen, stored once however many timelines show it, so an update reaches
 * every one of them.
 *
 * @property payloadJson the domain status, as JSON.
 * @property cachedAt when it was last written; a status no timeline points at goes a week after that.
 * @property authorId who posted it, and [boostedAuthorId] who posted what it boosts: blocking either
 *   removes it everywhere at once.
 * @property reblogOfId what a boost boosts, so updating that post updates every boost of it too.
 */
@Entity(
    tableName = "status",
    primaryKeys = ["accountId", "serverId"],
    indices = [Index("accountId", "cachedAt"), Index("accountId", "reblogOfId")],
)
public data class CachedStatusEntity(
    val accountId: String,
    val serverId: String,
    val payloadJson: String,
    val cachedAt: Long,
    val contentKind: String,
    val plainText: String,
    val authorId: String,
    val boostedAuthorId: String?,
    val reblogOfId: String?,
)

/**
 * One row of one timeline, pointing at a status or standing for a gap. [position] is the timeline's
 * own counter, descending down the list; it is not the creation date, which a boost and a late
 * federated post both break.
 */
@Entity(
    tableName = "timeline_entry",
    primaryKeys = ["accountId", "timelineKey", "statusId"],
    indices = [Index("accountId", "timelineKey", "position"), Index("accountId", "statusId")],
)
public data class TimelineEntryEntity(
    val accountId: String,
    val timelineKey: String,
    val statusId: String,
    val position: Long,
    val isGap: Boolean,
    val insertedAt: Long,
)

/**
 * Where a person left a timeline: the row at the top of the screen and how far it was scrolled past.
 * Kept with the cache because it points into it: clearing the cache clears where it pointed.
 */
@Entity(tableName = "timeline_position", primaryKeys = ["accountId", "timelineKey"])
public data class TimelinePositionEntity(
    val accountId: String,
    val timelineKey: String,
    val statusId: String,
    val offset: Int,
)

/**
 * How far the reader got in a video, mirrored from what was reported to the server so a progress bar is
 * right offline. A video watched to its end has no row: the server forgets it, and so does this.
 */
@Entity(tableName = "watch_position", primaryKeys = ["accountId", "statusId"])
public data class WatchPositionEntity(
    val accountId: String,
    val statusId: String,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val updatedAt: Long,
)

/** A v2 filter of one account, as JSON, with its expiry lifted out for the sweep. */
@Entity(tableName = "filter", primaryKeys = ["accountId", "id"])
public data class FilterEntity(val accountId: String, val id: String, val payloadJson: String, val expiresAt: Long?)

/** A timeline row with the status it points at; [payloadJson] is null for a gap or a status gone missing. */
public data class TimelineRowRecord(
    val statusId: String,
    val position: Long,
    val isGap: Boolean,
    val payloadJson: String?,
    val cachedAt: Long?,
)

@Dao
public interface TimelineDao {
    /**
     * The first [limit] rows of a timeline, with their statuses. Observing it re-emits when either
     * table changes, so an edited or favourited status shows up in every timeline that holds it.
     */
    @Query(
        """
        SELECT e.statusId, e.position, e.isGap, s.payloadJson, s.cachedAt
        FROM timeline_entry e
        LEFT JOIN status s ON s.accountId = e.accountId AND s.serverId = e.statusId
        WHERE e.accountId = :accountId AND e.timelineKey = :timelineKey
        ORDER BY e.position DESC
        LIMIT :limit
        """,
    )
    public fun observeRows(accountId: String, timelineKey: String, limit: Int): Flow<List<TimelineRowRecord>>

    @Query(
        """
        SELECT * FROM timeline_entry WHERE accountId = :accountId AND timelineKey = :timelineKey
        ORDER BY position DESC
        """,
    )
    public suspend fun entries(accountId: String, timelineKey: String): List<TimelineEntryEntity>

    @Query("DELETE FROM timeline_entry WHERE accountId = :accountId AND timelineKey = :timelineKey")
    public suspend fun clear(accountId: String, timelineKey: String)

    @Upsert
    public suspend fun upsertEntries(entries: List<TimelineEntryEntity>)

    @Upsert
    public suspend fun upsertStatuses(statuses: List<CachedStatusEntity>)

    /** A merged page in one transaction: the statuses it carried and the timeline's new order. */
    @Transaction
    public suspend fun apply(
        accountId: String,
        timelineKey: String,
        statuses: List<CachedStatusEntity>,
        entries: List<TimelineEntryEntity>,
    ) {
        upsertStatuses(statuses)
        clear(accountId, timelineKey)
        upsertEntries(entries)
    }
}

/** A stored status's classification, as `ContentKind`'s name. */
public data class StoredKind(val serverId: String, val contentKind: String)

@Dao
public interface StatusDao {
    @Query("SELECT * FROM status WHERE accountId = :accountId AND serverId = :serverId")
    public suspend fun get(accountId: String, serverId: String): CachedStatusEntity?

    @Query("SELECT * FROM status WHERE accountId = :accountId AND serverId = :serverId")
    public fun observe(accountId: String, serverId: String): Flow<CachedStatusEntity?>

    /** At most 900 ids at a time, under SQLite's limit on bound variables. */
    @Query("SELECT * FROM status WHERE accountId = :accountId AND serverId IN (:serverIds)")
    public fun observeMany(accountId: String, serverIds: List<String>): Flow<List<CachedStatusEntity>>

    /** What each of [serverIds] was classified as, where stored; at most 900 ids at a time. */
    @Query("SELECT serverId, contentKind FROM status WHERE accountId = :accountId AND serverId IN (:serverIds)")
    public suspend fun kinds(accountId: String, serverIds: List<String>): List<StoredKind>

    @Upsert
    public suspend fun upsert(status: CachedStatusEntity)

    @Upsert
    public suspend fun upsertAll(statuses: List<CachedStatusEntity>)

    /**
     * Every boost of any of [serverIds], which carries a copy of what it boosts that must change when it
     * does; at most as many ids as SQLite binds at once.
     */
    @Query("SELECT * FROM status WHERE accountId = :accountId AND reblogOfId IN (:serverIds)")
    public suspend fun boostsOfAny(accountId: String, serverIds: List<String>): List<CachedStatusEntity>

    /** A deleted status, or one that answered 404: gone, with every boost of it, and from every timeline. */
    @Transaction
    public suspend fun delete(accountId: String, serverId: String) {
        deleteEntries(accountId, serverId)
        deleteStatus(accountId, serverId)
    }

    // the boosts' entries go before the boosts, which name them
    @Query(
        """
        DELETE FROM timeline_entry WHERE accountId = :accountId AND (statusId = :serverId OR statusId IN
            (SELECT serverId FROM status WHERE accountId = :accountId AND reblogOfId = :serverId))
        """,
    )
    public suspend fun deleteEntries(accountId: String, serverId: String)

    @Query("DELETE FROM status WHERE accountId = :accountId AND (serverId = :serverId OR reblogOfId = :serverId)")
    public suspend fun deleteStatus(accountId: String, serverId: String)

    /**
     * Everything by [authorId], posted or boosted, gone from every timeline at once: blocking someone and
     * still seeing them is not blocking.
     */
    @Transaction
    public suspend fun deleteByAuthor(accountId: String, authorId: String) {
        deleteEntriesByAuthor(accountId, authorId)
        deleteStatusesByAuthor(accountId, authorId)
    }

    @Query(
        """
        DELETE FROM timeline_entry WHERE accountId = :accountId AND statusId IN
            (SELECT serverId FROM status WHERE accountId = :accountId AND (authorId = :authorId OR boostedAuthorId = :authorId))
        """,
    )
    public suspend fun deleteEntriesByAuthor(accountId: String, authorId: String)

    @Query("DELETE FROM status WHERE accountId = :accountId AND (authorId = :authorId OR boostedAuthorId = :authorId)")
    public suspend fun deleteStatusesByAuthor(accountId: String, authorId: String)

    /** Up to [limit] statuses older than [cutoff] that no timeline points at; returns how many went. */
    @Query(
        """
        DELETE FROM status WHERE rowid IN (
            SELECT s.rowid FROM status s
            WHERE s.cachedAt < :cutoff AND NOT EXISTS (
                SELECT 1 FROM timeline_entry e WHERE e.accountId = s.accountId AND e.statusId = s.serverId
            )
            LIMIT :limit
        )
        """,
    )
    public suspend fun deleteOrphans(cutoff: Long, limit: Int): Int
}

@Dao
public interface FilterDao {
    @Query("SELECT * FROM filter WHERE accountId = :accountId")
    public fun observe(accountId: String): Flow<List<FilterEntity>>

    @Transaction
    public suspend fun replace(accountId: String, filters: List<FilterEntity>) {
        clear(accountId)
        upsert(filters)
    }

    @Query("DELETE FROM filter WHERE accountId = :accountId")
    public suspend fun clear(accountId: String)

    @Upsert
    public suspend fun upsert(filters: List<FilterEntity>)

    @Query("DELETE FROM filter WHERE accountId = :accountId AND id = :id")
    public suspend fun delete(accountId: String, id: String)
}

@Dao
public interface PositionDao {
    @Query("SELECT * FROM timeline_position WHERE accountId = :accountId AND timelineKey = :timelineKey")
    public suspend fun get(accountId: String, timelineKey: String): TimelinePositionEntity?

    @Upsert
    public suspend fun set(position: TimelinePositionEntity)
}

@Dao
public interface WatchPositionDao {
    /** Every video [accountId] is part way through, as they change. */
    @Query("SELECT * FROM watch_position WHERE accountId = :accountId")
    public fun observe(accountId: String): Flow<List<WatchPositionEntity>>

    @Query("SELECT * FROM watch_position WHERE accountId = :accountId AND statusId = :statusId")
    public suspend fun get(accountId: String, statusId: String): WatchPositionEntity?

    @Upsert
    public suspend fun set(position: WatchPositionEntity)

    @Query("DELETE FROM watch_position WHERE accountId = :accountId AND statusId = :statusId")
    public suspend fun forget(accountId: String, statusId: String)
}

/** Removing an account takes everything it cached with it, at once. */
@Dao
public interface CacheAccountDao {
    @Transaction
    public suspend fun deleteEverything(accountId: String) {
        deleteEntries(accountId)
        deleteStatuses(accountId)
        deleteFilters(accountId)
        deletePositions(accountId)
        deleteWatchPositions(accountId)
    }

    @Query("DELETE FROM watch_position WHERE accountId = :accountId")
    public suspend fun deleteWatchPositions(accountId: String)

    @Query("DELETE FROM timeline_position WHERE accountId = :accountId")
    public suspend fun deletePositions(accountId: String)

    @Query("DELETE FROM timeline_entry WHERE accountId = :accountId")
    public suspend fun deleteEntries(accountId: String)

    @Query("DELETE FROM status WHERE accountId = :accountId")
    public suspend fun deleteStatuses(accountId: String)

    @Query("DELETE FROM filter WHERE accountId = :accountId")
    public suspend fun deleteFilters(accountId: String)
}

/**
 * `cache.db`: disposable. Everything in it can be fetched again, so a schema change drops it rather
 * than migrating, and "clear cached content" deletes the file.
 */
@Database(
    entities = [
        CachedStatusEntity::class,
        TimelineEntryEntity::class,
        FilterEntity::class,
        TimelinePositionEntity::class,
        WatchPositionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
public abstract class CacheDatabase : RoomDatabase() {
    public abstract fun timelineDao(): TimelineDao

    public abstract fun statusDao(): StatusDao

    public abstract fun filterDao(): FilterDao

    public abstract fun cacheAccountDao(): CacheAccountDao

    public abstract fun positionDao(): PositionDao

    public abstract fun watchPositionDao(): WatchPositionDao

    public companion object {
        public const val FILE_NAME: String = "cache.db"
    }
}
