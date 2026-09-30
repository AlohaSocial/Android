// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One signed-in account. Never holds a secret: the token and any app password live in the vault,
 * keyed by [id]. A restored backup of this table without its vault lists every account as needing a
 * new sign-in.
 *
 * @property capabilitiesJson the detected server capabilities, refreshed every 24 hours.
 * @property profilePending set while the server cannot describe a brand-new account yet (Nextcloud
 *   Social answers 500 from `verify_credentials` until its avatar cache job has run).
 */
@Entity(tableName = "account")
public data class AccountEntity(
    @PrimaryKey val id: String,
    val instanceHost: String,
    val apiBase: String,
    val serverAccountId: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    val headerUrl: String?,
    val capabilitiesJson: String?,
    val needsReauth: Boolean,
    val profilePending: Boolean,
    val sortIndex: Int,
    val addedAt: Long,
    val nextcloudConnected: Boolean,
)

/**
 * The app's OAuth client on one server. The client secret lives in the vault, keyed by [host].
 */
@Entity(tableName = "client_registration")
public data class ClientRegistrationEntity(
    @PrimaryKey val host: String,
    val clientId: String,
    val scopes: String,
    val registeredAt: Long,
)

@Dao
public interface AccountDao {
    @Query("SELECT * FROM account ORDER BY sortIndex, addedAt")
    public fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account ORDER BY sortIndex, addedAt")
    public suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM account WHERE id = :id")
    public suspend fun get(id: String): AccountEntity?

    @Query("SELECT * FROM account WHERE instanceHost = :host AND serverAccountId = :serverAccountId")
    public suspend fun find(host: String, serverAccountId: String): AccountEntity?

    @Upsert
    public suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM account WHERE id = :id")
    public suspend fun delete(id: String)

    /** What the account looks like, as its owner just changed it; nothing else of the row. */
    @Query(
        "UPDATE account SET displayName = :displayName, avatarUrl = :avatarUrl, headerUrl = :headerUrl, " +
            "profilePending = 0 WHERE id = :id",
    )
    public suspend fun setProfile(id: String, displayName: String, avatarUrl: String?, headerUrl: String?)

    @Query("UPDATE account SET needsReauth = :needsReauth WHERE id = :id")
    public suspend fun setNeedsReauth(id: String, needsReauth: Boolean)

    @Query("UPDATE account SET nextcloudConnected = :connected WHERE id = :id")
    public suspend fun setNextcloudConnected(id: String, connected: Boolean)

    @Query("UPDATE account SET needsReauth = 1")
    public suspend fun markAllNeedReauth()

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM account")
    public suspend fun nextSortIndex(): Int

    @Query("SELECT * FROM client_registration WHERE host = :host")
    public suspend fun registration(host: String): ClientRegistrationEntity?

    @Upsert
    public suspend fun upsertRegistration(registration: ClientRegistrationEntity)

    @Query("DELETE FROM client_registration WHERE host = :host")
    public suspend fun deleteRegistration(host: String)
}

/** `accounts.db`: durable, versioned, migrated and never destroyed. */
@Database(entities = [AccountEntity::class, ClientRegistrationEntity::class], version = 1, exportSchema = true)
public abstract class AccountsDatabase : RoomDatabase() {
    public abstract fun accountDao(): AccountDao

    public companion object {
        public const val FILE_NAME: String = "accounts.db"
    }
}
