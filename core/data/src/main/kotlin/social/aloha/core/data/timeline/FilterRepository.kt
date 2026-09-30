// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import social.aloha.core.data.ClientFactory
import social.aloha.core.database.CacheAccountDao
import social.aloha.core.database.FilterDao
import social.aloha.core.database.FilterEntity
import social.aloha.core.database.StatusDao
import social.aloha.core.model.Filter
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.FilterEndpoints

/**
 * An account's v2 filters, fetched from the server and kept in the cache so they apply to cached rows
 * at once. Managing them is a later screen; this reads and refreshes.
 */
@Singleton
public class FilterRepository @Inject constructor(private val dao: FilterDao, private val clients: ClientFactory) {
    /** Every stored filter, expired ones included: [FilterEvaluator] skips those when it draws. */
    public fun observe(accountId: String): Flow<List<Filter>> = dao.observe(accountId).map { rows ->
        rows.mapNotNull { row ->
            try {
                StatusRepository.json.decodeFromString(Filter.serializer(), row.payloadJson)
            } catch (_: SerializationException) {
                null
            }
        }
    }

    /** Replaces the stored filters with the server's; a failure keeps the ones stored. */
    public suspend fun refresh(account: SignedInAccount): ApiError? {
        val client = clients.forAccount(account) ?: return ApiError.NotFound
        return when (val result = client.execute(FilterEndpoints.all())) {
            is ApiResult.Failure -> result.error

            is ApiResult.Success -> null.also {
                dao.replace(
                    account.id,
                    result.value.map {
                        FilterEntity(
                            account.id,
                            it.id,
                            StatusRepository.json.encodeToString(Filter.serializer(), it),
                            it.expiresAt?.toEpochMilli(),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Keeps the disposable cache within bounds: statuses no timeline points at go a week after they were
 * last written, at most [CachePolicy.MAXIMUM_DELETIONS_PER_SWEEP] at a time, so a sweep never stalls
 * a launch. Timeline windows are capped when they are written, so they need no sweep.
 *
 * ponytail: runs once per launch from the app's maintenance, off the main thread; it moves to a
 * charging-only periodic WorkManager job when `:core:sync` brings WorkManager in.
 */
@Singleton
public class CacheSweeper @Inject constructor(
    private val statuses: StatusDao,
    private val accounts: CacheAccountDao,
    private val clock: java.time.Clock,
) {
    /** How many statuses went. */
    public suspend fun sweep(): Int {
        val cutoff = clock.millis() - java.util.concurrent.TimeUnit.DAYS.toMillis(CachePolicy.ORPHAN_STATUS_DAYS)
        return statuses.deleteOrphans(cutoff, CachePolicy.MAXIMUM_DELETIONS_PER_SWEEP)
    }

    /** Everything an account cached, gone at once when the account is removed. */
    public suspend fun forget(accountId: String) {
        accounts.deleteEverything(accountId)
    }
}
