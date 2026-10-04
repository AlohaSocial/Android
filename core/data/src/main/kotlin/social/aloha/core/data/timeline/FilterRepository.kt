// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.database.CacheAccountDao
import social.aloha.core.database.FilterDao
import social.aloha.core.database.FilterEntity
import social.aloha.core.database.StatusDao
import social.aloha.core.model.Filter
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.FilterDraft
import social.aloha.core.network.endpoints.FilterEndpoints
import timber.log.Timber

/**
 * An account's v2 filters, fetched from the server and kept in the cache so they apply to cached rows
 * at once: one saved or deleted here is written to the cache as the server answers, so what it hides
 * goes, or comes back, without a refetch.
 */
@Singleton
public class FilterRepository @Inject constructor(private val dao: FilterDao, private val clients: ClientFactory) {
    /** Every stored filter, expired ones included: [FilterEvaluator] skips those when it draws. */
    public fun observe(accountId: String): Flow<List<Filter>> = dao.observe(accountId).map { rows ->
        rows.mapNotNull { row ->
            try {
                StatusRepository.json.decodeFromString(Filter.serializer(), row.payloadJson)
            } catch (e: SerializationException) {
                Timber.tag(LogArea.App.name).w("Filter %s unreadable: %s", row.id, e.javaClass.simpleName)
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
                dao.replace(account.id, result.value.map { it.entity(account.id) })
            }
        }
    }

    /** Filter [id] as the server has it now, kept on the device as it answers. */
    public suspend fun get(account: SignedInAccount, id: String): Answer<Filter> =
        clients.answer(account, FilterEndpoints.get(id)).also { answer ->
            if (answer is Answer.Got) dao.upsert(listOf(answer.value.entity(account.id)))
        }

    /** Creates a filter from [draft], or changes filter [id]; the server's answer is the one kept. */
    public suspend fun save(account: SignedInAccount, id: String?, draft: FilterDraft): Answer<Filter> {
        val request = if (id == null) FilterEndpoints.create(draft) else FilterEndpoints.update(id, draft)
        return clients.answer(account, request).also { answer ->
            if (answer is Answer.Got) dao.upsert(listOf(answer.value.entity(account.id)))
        }
    }

    public suspend fun delete(account: SignedInAccount, id: String): Answer<Unit> =
        clients.answer(account, FilterEndpoints.delete(id)).also { answer ->
            if (answer is Answer.Got) dao.delete(account.id, id)
        }

    private fun Filter.entity(accountId: String) = FilterEntity(
        accountId,
        id,
        StatusRepository.json.encodeToString(Filter.serializer(), this),
        expiresAt?.toEpochMilli(),
    )
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
