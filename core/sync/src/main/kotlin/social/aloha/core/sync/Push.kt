// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.content.pm.PackageManager
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.sync.PushSubscriptions
import social.aloha.core.model.SignedInAccount

/** A push distributor installed on the device: its package, and the name it shows. */
public data class Distributor(val packageName: String, val label: String)

/**
 * Push through UnifiedPush: the person picks a distributor, such as ntfy, and each account whose server
 * offers Web Push (a `vapid_key`) registers with it, one registration per account. Nextcloud Social
 * offers none; its push comes through the Nextcloud. An account without push is polled as before.
 */
public class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val subscriptions: PushSubscriptions,
) {
    /** The distributors installed on the device. */
    public fun distributors(): List<Distributor> = UnifiedPush.getDistributors(context).map { name ->
        val label = try {
            context.packageManager.run { getApplicationLabel(getApplicationInfo(name, 0)).toString() }
        } catch (_: PackageManager.NameNotFoundException) {
            name
        }
        Distributor(name, label)
    }

    /** The distributor chosen, if it is still installed. */
    public fun current(): String? = UnifiedPush.getSavedDistributor(context)

    /** Uses [packageName] from now on, or none; every account registers again with it. */
    public suspend fun choose(packageName: String?) {
        val all = accounts.all()
        all.forEach { UnifiedPush.unregister(context, it.id) }
        if (packageName == null) {
            UnifiedPush.removeDistributor(context)
            all.forEach { subscriptions.unsubscribe(it) }
            return
        }
        UnifiedPush.saveDistributor(context, packageName)
        registerAll()
    }

    /** Registers every account that can be pushed to with the chosen distributor; idempotent. */
    public suspend fun registerAll() {
        if (current() == null) return
        accounts.all().filterNot { it.needsReauth }
            .mapNotNull { account ->
                account.capabilities.webPushVapidKey?.takeIf { it.isNotEmpty() }?.let {
                    account to
                        it
                }
            }
            .forEach { (account, vapid) -> register(account, vapid) }
    }

    private fun register(account: SignedInAccount, vapid: String) {
        try {
            UnifiedPush.register(context, instance = account.id, vapid = vapid)
        } catch (_: UnifiedPush.VapidNotValidException) {
            // a key in a form Web Push does not take: this account stays polled
        }
    }

    /** Stops [account]'s push on its server and on the device, before it signs out. */
    public suspend fun forget(account: SignedInAccount) {
        if (subscriptions.isActive(account.id)) subscriptions.unsubscribe(account)
        UnifiedPush.unregister(context, account.id)
    }
}

/**
 * What the distributor tells the app. It all goes to WorkManager, since a service handed a push has
 * seconds; a push itself carries no content read here, it only says to ask the account's server now,
 * so what is raised is decided, worded and deduplicated exactly as a poll does.
 */
public class AlohaPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet ?: return
        PushWork.enqueue(this, instance, PushWork.Job.Subscribe, endpoint.url, keys.pubKey, keys.auth)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        PushWork.enqueue(this, instance, PushWork.Job.Poll)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        PushWork.enqueue(this, instance, PushWork.Job.Lost)
    }

    override fun onUnregistered(instance: String) {
        PushWork.enqueue(this, instance, PushWork.Job.Lost)
    }
}

/** The work a push event turns into: subscribe the new endpoint, ask the server now, or fall back to polling. */
@HiltWorker
internal class PushWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val subscriptions: PushSubscriptions,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    enum class Job { Subscribe, Poll, Lost }

    override suspend fun doWork(): Result {
        val account = inputData.getString(ACCOUNT)?.let { accounts.byId(it) } ?: return Result.success()
        when (Job.valueOf(inputData.getString(JOB) ?: Job.Poll.name)) {
            Job.Subscribe -> {
                val error = subscriptions.subscribe(
                    account,
                    inputData.getString(ENDPOINT).orEmpty(),
                    inputData.getString(P256DH).orEmpty(),
                    inputData.getString(AUTH).orEmpty(),
                )
                if (error != null && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
            }

            Job.Poll -> engine.poll(account, PollScope.NotificationsOnly)

            Job.Lost -> subscriptions.lost(account.id)
        }
        return Result.success()
    }

    companion object {
        private const val ACCOUNT = "account"
        private const val JOB = "job"
        private const val ENDPOINT = "endpoint"
        private const val P256DH = "p256dh"
        private const val AUTH = "auth"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(
            context: Context,
            accountId: String,
            job: Job,
            endpoint: String? = null,
            p256dh: String? = null,
            auth: String? = null,
        ) {
            val request = OneTimeWorkRequestBuilder<PushWork>()
                .setInputData(
                    workDataOf(
                        ACCOUNT to accountId,
                        JOB to job.name,
                        ENDPOINT to endpoint,
                        P256DH to p256dh,
                        AUTH to auth,
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // a push during a poll asks once more after it, so nothing it brought waits for the next poll;
            // an endpoint always replaces the last one
            val policy = if (job == Job.Poll) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork("push-$accountId-${job.name}", policy, request)
        }
    }
}
