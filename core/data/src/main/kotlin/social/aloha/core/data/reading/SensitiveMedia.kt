// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.reading

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.InstanceEndpoints

/**
 * How each account shows media marked sensitive: its own `reading:expand:media` preference, which the
 * server keeps. Until the server has said, and wherever it cannot, media is covered, one press away.
 */
@Singleton
public class SensitiveMedia @Inject constructor(private val clients: ClientFactory) {
    private val policies = MutableStateFlow(emptyMap<String, SensitiveMediaPolicy>())

    public fun policy(accountId: String): Flow<SensitiveMediaPolicy> = policies.map { it[accountId] ?: DEFAULT }

    /** Asks [account]'s server again; a server that does not answer leaves what was known. */
    public suspend fun refresh(account: SignedInAccount) {
        val answer = clients.answer(account, InstanceEndpoints.preferences()) as? Answer.Got ?: return
        remember(account.id, answer.value.expandMedia)
    }

    /**
     * Sets [policy] on [account]'s server, which only Nextcloud Social takes (`PUT /api/v1/preferences`;
     * Mastodon changes it on its website). False when the server refused, and nothing changed.
     */
    public suspend fun choose(account: SignedInAccount, policy: SensitiveMediaPolicy): Boolean {
        val taken = clients.answer(account, InstanceEndpoints.setExpandMedia(policy.wire)) is Answer.Got
        if (taken) remember(account.id, policy)
        return taken
    }

    private fun remember(accountId: String, policy: SensitiveMediaPolicy?) {
        // a value this build does not know covers, as an unknown one must never show more
        val known = policy?.takeIf { it != SensitiveMediaPolicy.Unknown } ?: DEFAULT
        policies.update { it + (accountId to known) }
    }

    private companion object {
        val DEFAULT = SensitiveMediaPolicy.Blur
    }
}
