// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.InstanceRule
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.endpoints.ReportDraft
import social.aloha.core.network.endpoints.SafetyEndpoints

/** Telling the reader's server's moderators about an account, and the rules they can point to. */
@Singleton
public class Reports @Inject constructor(private val clients: ClientFactory) {
    /** The rules of the reader's server, which a report can say were broken. */
    public suspend fun rules(reader: SignedInAccount): Answer<List<InstanceRule>> =
        clients.answer(reader, InstanceEndpoints.rules())

    public suspend fun send(reader: SignedInAccount, report: ReportDraft): Answer<Unit> =
        clients.answer(reader, SafetyEndpoints.report(report))
}
