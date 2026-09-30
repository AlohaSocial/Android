// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.map
import social.aloha.core.model.ScheduledStatus
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.ComposeEndpoints
import social.aloha.core.network.endpoints.StatusPost

/**
 * Posts that wait on the server for their time: scheduling one, and the list the writer manages.
 * Nothing is kept here; the server holds them, so they go out whether or not the phone is on.
 */
@Singleton
public class ScheduledPosts @Inject constructor(private val clients: ClientFactory) {
    /** Schedules [post] as [reader] for its `scheduledAt`, at least five minutes ahead. */
    public suspend fun schedule(reader: SignedInAccount, post: StatusPost): Answer<ScheduledStatus> =
        clients.answer(reader, ComposeEndpoints.schedule(post))

    /** What [reader] has scheduled, soonest first. */
    public suspend fun all(reader: SignedInAccount): Answer<List<ScheduledStatus>> =
        clients.answer(reader, ComposeEndpoints.scheduled()).map { list -> list.sortedBy { it.scheduledAt } }

    public suspend fun delete(reader: SignedInAccount, id: String): Answer<Unit> =
        clients.answer(reader, ComposeEndpoints.deleteScheduled(id))

    /** Moves post [id] to [at]. */
    public suspend fun reschedule(reader: SignedInAccount, id: String, at: Instant): Answer<ScheduledStatus> =
        clients.answer(reader, ComposeEndpoints.reschedule(id, at))
}
