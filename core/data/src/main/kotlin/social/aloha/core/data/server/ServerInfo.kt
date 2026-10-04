// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.server

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.InstanceActivityWeek
import social.aloha.core.model.InstanceDocument
import social.aloha.core.model.InstanceRule
import social.aloha.core.model.PublicDomainBlock
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.endpoints.InstanceEndpoints

/**
 * What a server publishes about itself. Each part is null where the server has none: a 404 (Nextcloud
 * Social and Mastodon answer it for a page the administrator never wrote), an error, or an empty one.
 */
public data class ServerAbout(
    val rules: List<InstanceRule>? = null,
    val description: InstanceDocument? = null,
    val privacyPolicy: InstanceDocument? = null,
    val termsOfService: InstanceDocument? = null,
    val activity: List<InstanceActivityWeek>? = null,
    val peers: List<String>? = null,
    val domainBlocks: List<PublicDomainBlock>? = null,
)

public class ServerInfo @Inject constructor(private val clients: ClientFactory) {
    /** Every page of [account]'s server, asked for at once. */
    public suspend fun about(account: SignedInAccount): ServerAbout = coroutineScope {
        val rules = async { list(account, InstanceEndpoints.rules()) }
        val description = async { page(account, InstanceEndpoints.extendedDescription()) }
        val privacy = async { page(account, InstanceEndpoints.privacyPolicy()) }
        val terms = async { page(account, InstanceEndpoints.termsOfService()) }
        val activity = async { list(account, InstanceEndpoints.activity()) }
        val peers = async { list(account, InstanceEndpoints.peers()) }
        val blocks = async { list(account, InstanceEndpoints.domainBlocks()) }
        ServerAbout(
            rules.await(),
            description.await(),
            privacy.await(),
            terms.await(),
            activity.await(),
            peers.await()?.sorted(),
            blocks.await(),
        )
    }

    private suspend fun <T> list(account: SignedInAccount, request: ApiRequest<List<T>>): List<T>? =
        (clients.answer(account, request) as? Answer.Got)?.value?.takeIf { it.isNotEmpty() }

    private suspend fun page(account: SignedInAccount, request: ApiRequest<InstanceDocument>): InstanceDocument? =
        (clients.answer(account, request) as? Answer.Got)?.value?.takeIf { it.content.isNotBlank() }
}
