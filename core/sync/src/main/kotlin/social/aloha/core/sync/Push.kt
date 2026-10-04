// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.content.pm.PackageManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.nextcloud.NextcloudConnection
import social.aloha.core.data.sync.PushSubscriptions
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import timber.log.Timber

/** A push distributor installed on the device: its package, and the name it shows. */
public data class Distributor(val packageName: String, val label: String)

/**
 * Push through UnifiedPush: the person picks a distributor, such as ntfy, and each account whose server
 * offers Web Push (a `vapid_key`) registers with it. Nextcloud Social offers none: an account connected
 * to its Nextcloud registers with the Nextcloud's notifications app instead, where that pushes. Each
 * registration is named after its account, so a push says whose it is. An account without push is
 * polled as before.
 */
public class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val subscriptions: PushSubscriptions,
    private val nextcloud: NextcloudConnection,
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
        all.forEach {
            UnifiedPush.unregister(context, it.id)
            UnifiedPush.unregister(context, PushInstance.nextcloud(it.id))
        }
        if (packageName == null) {
            Timber.tag(LogArea.Push.name).i("Push turned off, polled instead")
            UnifiedPush.removeDistributor(context)
            // the servers and Nextclouds stop pushing to an endpoint nobody reads any more
            all.forEach { stopOnServer(it) }
            return
        }
        Timber.tag(LogArea.Push.name).i("Distributor %s chosen", packageName)
        UnifiedPush.saveDistributor(context, packageName)
        registerAll()
    }

    /** Registers every account that can be pushed to with the chosen distributor; idempotent. */
    public suspend fun registerAll() {
        if (current() == null) return
        accounts.all().filterNot { it.needsReauth }.forEach { register(it) }
    }

    /** With its server where that pushes, else with its Nextcloud where that does. */
    private suspend fun register(account: SignedInAccount) {
        val server = account.capabilities.webPushVapidKey?.takeIf { it.isNotEmpty() }
        when {
            server != null -> register(account.id, server)

            account.nextcloudConnected -> nextcloud.webPushVapid(account)?.let {
                register(PushInstance.nextcloud(account.id), it)
            }
        }
    }

    private fun register(instance: String, vapid: String) {
        try {
            UnifiedPush.register(context, instance = instance, vapid = vapid)
        } catch (_: UnifiedPush.VapidNotValidException) {
            Timber.tag(LogArea.Push.name).w("VAPID key refused for %s, polled instead", instance)
        }
    }

    /** Stops [account]'s push on its server, its Nextcloud and the device, before it signs out. */
    public suspend fun forget(account: SignedInAccount) {
        stopOnServer(account)
        UnifiedPush.unregister(context, account.id)
    }

    /** Whichever pushes to [account]: its server, its Nextcloud, or both, each told to stop. */
    private suspend fun stopOnServer(account: SignedInAccount) {
        if (!account.capabilities.webPushVapidKey.isNullOrEmpty()) subscriptions.unsubscribe(account)
        if (account.nextcloudConnected) {
            nextcloud.unregisterWebPush(account)
            forgetNextcloud(account)
        }
    }

    /**
     * Stops [account]'s push through its Nextcloud on the device; disconnecting removes the registration
     * on the Nextcloud itself.
     */
    public suspend fun forgetNextcloud(account: SignedInAccount) {
        subscriptions.lost(account.id)
        subscriptions.rememberEndpoint(PushInstance.nextcloud(account.id), null)
        UnifiedPush.unregister(context, PushInstance.nextcloud(account.id))
    }
}

/** A registration's name: the account's id, prefixed for the one made with its Nextcloud. */
internal object PushInstance {
    private const val NEXTCLOUD = "nextcloud:"

    fun nextcloud(accountId: String) = NEXTCLOUD + accountId

    /** The account a registration belongs to, and whether it is the Nextcloud one. */
    fun of(instance: String): Pair<String, Boolean> =
        if (instance.startsWith(NEXTCLOUD)) instance.removePrefix(NEXTCLOUD) to true else instance to false
}

/**
 * What the distributor tells the app. It all goes to WorkManager, since a service handed a push has
 * seconds; a push itself carries no content read here, it only says to ask the account's server now,
 * so what is raised is decided, worded and deduplicated exactly as a poll does.
 */
public class AlohaPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet ?: return
        val (account, viaNextcloud) = PushInstance.of(instance)
        Timber.tag(LogArea.Push.name).i("New endpoint for %s", instance)
        val job = if (viaNextcloud) PushWork.Job.SubscribeNextcloud else PushWork.Job.Subscribe
        PushWork.enqueue(this, account, job, endpoint.url, keys.pubKey, keys.auth, instance = instance)
    }

    /**
     * A push only says to ask the server now. The one exception is the Nextcloud's first push, whose
     * only content is the token that confirms the registration.
     */
    override fun onMessage(message: PushMessage, instance: String) {
        val (account, viaNextcloud) = PushInstance.of(instance)
        val token = if (viaNextcloud && message.decrypted) activationToken(message.content) else null
        if (token != null) {
            PushWork.enqueue(this, account, PushWork.Job.Activate, token = token)
        } else {
            PushWork.enqueue(this, account, PushWork.Job.Poll)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Timber.tag(LogArea.Push.name).w("Registration of %s failed: %s", instance, reason)
        PushWork.enqueue(this, PushInstance.of(instance).first, PushWork.Job.Lost)
    }

    override fun onUnregistered(instance: String) {
        Timber.tag(LogArea.Push.name).i("%s unregistered", instance)
        PushWork.enqueue(this, PushInstance.of(instance).first, PushWork.Job.Lost)
    }

    private fun activationToken(content: ByteArray): String? = try {
        JSONObject(content.decodeToString()).optString("activationToken").ifEmpty { null }
    } catch (_: JSONException) {
        null
    }
}

/** The work a push event turns into: subscribe the new endpoint, ask the server now, or fall back to polling. */
@HiltWorker
internal class PushWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val subscriptions: PushSubscriptions,
    private val nextcloud: NextcloudConnection,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    enum class Job { Subscribe, SubscribeNextcloud, Activate, Poll, Lost }

    // a push is asked for at once where the system allows; below Android 12 that needs a notification
    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(id.hashCode(), checking(applicationContext))

    override suspend fun doWork(): Result {
        val account = inputData.getString(ACCOUNT)?.let { accounts.byId(it) } ?: return Result.success()
        val job = Job.valueOf(inputData.getString(JOB) ?: Job.Poll.name)
        val failed = run(account, job)
        if (failed) Timber.tag(LogArea.Push.name).i("%s for %s failed, attempt %d", job, account.id, runAttemptCount)
        return if (failed && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    /** Does [job] for [account]; true when it failed in a way worth trying again. */
    private suspend fun run(account: SignedInAccount, job: Job): Boolean = when (job) {
        // the same endpoint again, as every start of the app hands it back, needs nothing new of the server
        Job.Subscribe, Job.SubscribeNextcloud -> if (subscriptions.subscribed(
                account.id,
                input(INSTANCE),
                input(ENDPOINT),
            )
        ) {
            false
        } else {
            subscribe(account, job)
        }

        // pushed to only once the Nextcloud has its token back
        Job.Activate -> {
            if (nextcloud.activateWebPush(account, input(TOKEN)) == null) subscriptions.markActive(account.id)
            false
        }

        Job.Poll -> {
            engine.poll(account, PollScope.NotificationsOnly)
            false
        }

        Job.Lost -> {
            subscriptions.lost(account.id)
            false
        }
    }

    private suspend fun subscribe(account: SignedInAccount, job: Job): Boolean {
        val error = if (job == Job.Subscribe) {
            subscriptions.subscribe(account, input(ENDPOINT), input(P256DH), input(AUTH))
        } else {
            nextcloud.registerWebPush(account, input(ENDPOINT), input(P256DH), input(AUTH))
        }
        if (error == null) subscriptions.rememberEndpoint(input(INSTANCE), input(ENDPOINT))
        return error != null
    }

    private fun input(key: String): String = inputData.getString(key).orEmpty()

    companion object {
        private const val ACCOUNT = "account"
        private const val JOB = "job"
        private const val ENDPOINT = "endpoint"
        private const val P256DH = "p256dh"
        private const val AUTH = "auth"
        private const val TOKEN = "token"
        private const val INSTANCE = "instance"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(
            context: Context,
            accountId: String,
            job: Job,
            endpoint: String? = null,
            p256dh: String? = null,
            auth: String? = null,
            token: String? = null,
            instance: String? = null,
        ) {
            val request = OneTimeWorkRequestBuilder<PushWork>()
                .setInputData(
                    workDataOf(
                        ACCOUNT to accountId,
                        JOB to job.name,
                        ENDPOINT to endpoint,
                        P256DH to p256dh,
                        AUTH to auth,
                        TOKEN to token,
                        INSTANCE to instance,
                    ),
                )
                .setConstraints(NEEDS_NETWORK)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            // a push during a poll asks once more after it, so nothing it brought waits for the next poll;
            // an endpoint always replaces the last one
            val policy = if (job == Job.Poll) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork("push-$accountId-${job.name}", policy, request)
        }
    }
}
