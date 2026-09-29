// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.model.InstanceDescription
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.probe.ProbeResult
import social.aloha.core.network.probe.ReprobeGate
import social.aloha.core.network.probe.ServerAddress
import social.aloha.core.network.probe.ServerProbe

/**
 * Keeps what the app knows about each server current. Once per launch every account's API base is
 * asked for its instance; one that stopped answering is probed for anew, at most once an hour per
 * host, because an administrator may have added or removed the rewrite rules. Capabilities are
 * detected again when they are older than a day or the base moved. Accounts that need a new sign-in
 * are left alone.
 */
@Singleton
public class AccountMaintenance @Inject constructor(
    private val accounts: AccountRepository,
    private val probe: ServerProbe,
    private val detector: CapabilityDetector,
    private val gate: ReprobeGate,
    private val clock: Clock,
) {
    public suspend fun checkAll() {
        accounts.all().filter { !it.needsReauth }.forEach { check(it) }
    }

    /** Checks [account]'s API base, finding it again when it moved, and detects capabilities when due. */
    public suspend fun check(account: SignedInAccount) {
        val current = account.apiBase.toHttpUrlOrNull() ?: return
        val answering = probe.instanceAt(current)
        when {
            answering == null -> findAgain(account, current)
            account.capabilities.isStale(clock.instant()) -> detect(account, current, answering)
        }
    }

    private suspend fun findAgain(account: SignedInAccount, current: HttpUrl) {
        val moved = reprobed(current) ?: return
        probe.instanceAt(moved)?.let { detect(account, moved, it) }
    }

    private suspend fun detect(account: SignedInAccount, base: HttpUrl, instance: InstanceDescription) {
        val nodeInfo = probe.nodeInfo(base.newBuilder().encodedPath("/").build())
        accounts.updateCapabilities(account.id, detector.detect(base, accounts.token(account.id), instance, nodeInfo))
    }

    private suspend fun reprobed(current: HttpUrl): HttpUrl? {
        if (!gate.tryAcquire(current.host)) return null
        val address = ServerAddress(current.newBuilder().encodedPath("/").build(), pathHint = null)
        return (probe.discover(address) as? ProbeResult.Found)?.outcome?.apiBase
    }
}
