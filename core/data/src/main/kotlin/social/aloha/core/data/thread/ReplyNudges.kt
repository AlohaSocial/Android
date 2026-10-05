// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.thread

import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.network.endpoints.AccountEndpoints

/** A moment's pause before a reply, shown as a sheet the reader can always answer with Reply. */
public enum class ReplyNudge {
    /** The post is more than [ReplyNudges.OLD] old: the reply may no longer be relevant. */
    OldPost,

    /** A first reply to someone who does not follow the reader: a few lines of etiquette. */
    Stranger,
}

/**
 * Which nudge, if any, comes before a reply, and the reader's "don't show again", for one account or
 * for all of them. A stranger is nudged about once: after that their name is remembered for the account.
 */
public class ReplyNudges @Inject constructor(
    private val app: AppPreferences,
    private val settings: AccountSettingsStore,
    private val clients: ClientFactory,
    private val clock: Clock,
) {
    /** The nudge before [reader] replies to [status], or null to reply at once. */
    public suspend fun before(reader: SignedInAccount, status: Status): ReplyNudge? {
        val shown = status.displayed
        val own = settings.settings(reader.id).first()
        val silenced = app.silencedNudges.first() + own.silencedNudges
        val old = Duration.between(shown.createdAt, clock.instant()) > OLD
        val author = shown.account.id
        return when {
            old && ReplyNudge.OldPost.name !in silenced -> ReplyNudge.OldPost

            author == reader.serverAccountId || ReplyNudge.Stranger.name in silenced -> null

            author in own.nudgedAuthors -> null

            // a relationship the server would not give is no reason to stop anyone replying
            followsReader(reader, author) != false -> null

            else -> ReplyNudge.Stranger
        }
    }

    /** [nudge] was shown about [authorId]: a stranger is not nudged about again on this account. */
    public suspend fun shown(reader: SignedInAccount, nudge: ReplyNudge, authorId: String) {
        if (nudge != ReplyNudge.Stranger) return
        settings.update(reader.id) {
            it.copy(nudgedAuthors = (it.nudgedAuthors - authorId + authorId).toList().takeLast(NUDGED_KEPT).toSet())
        }
    }

    /** [nudge] is not shown again: on [reader]'s account, or on every account [everywhere]. */
    public suspend fun silence(reader: SignedInAccount, nudge: ReplyNudge, everywhere: Boolean) {
        if (everywhere) {
            app.silenceNudge(nudge.name)
        } else {
            settings.update(reader.id) { it.copy(silencedNudges = it.silencedNudges + nudge.name) }
        }
    }

    private suspend fun followsReader(reader: SignedInAccount, author: String): Boolean? =
        (clients.answer(reader, AccountEndpoints.relationships(listOf(author))) as? Answer.Got)
            ?.value?.firstOrNull()?.followedBy

    public companion object {
        public val OLD: Duration = Duration.ofDays(90)

        /** How many authors are remembered as nudged about, the newest kept. */
        private const val NUDGED_KEPT = 500
    }
}
