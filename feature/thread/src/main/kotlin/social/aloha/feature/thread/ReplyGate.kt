// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.thread.ReplyNudge
import social.aloha.core.data.thread.ReplyNudges
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status

/** A reply on its way: the nudge waiting for an answer, or the post to reply to now. */
internal data class ReplyState(val nudge: Pair<ReplyNudge, Status>? = null, val replyTo: String? = null)

/**
 * What stands between a reply and the composer in a thread: a nudge, once per post, or nothing. [status]
 * finds a stored post by id and [account] the reader, both as the thread knows them now.
 */
internal class ReplyGate(
    private val scope: CoroutineScope,
    private val nudges: ReplyNudges,
    private val account: () -> SignedInAccount?,
    private val status: (String) -> Status?,
) {
    private val replies = MutableStateFlow(ReplyState())
    private val nudged = mutableSetOf<String>()

    val state: StateFlow<ReplyState> = replies.asStateFlow()

    /** A reply to [statusId]: at once, or after a nudge, once per post in this thread. */
    fun onReply(statusId: String) {
        val post = status(statusId)
        val reader = account()
        if (post == null || reader == null || statusId in nudged) return replies.update { it.copy(replyTo = statusId) }
        scope.launch {
            val nudge = nudges.before(reader, post)
            nudged += statusId
            if (nudge == null) {
                replies.update { it.copy(replyTo = statusId) }
            } else {
                nudges.shown(reader, nudge, post.displayed.account.id)
                replies.update { it.copy(nudge = nudge to post) }
            }
        }
    }

    /** The nudge answered: a reply opens when [reply]; [silence] turns it off here, or [everywhere]. */
    fun onNudged(reply: Boolean, silence: Boolean = false, everywhere: Boolean = false) {
        val (nudge, post) = replies.value.nudge ?: return
        if (silence) account()?.let { scope.launch { nudges.silence(it, nudge, everywhere) } }
        replies.update { ReplyState(replyTo = if (reply) post.id else null) }
    }

    fun onReplyOpened() {
        replies.update { it.copy(replyTo = null) }
    }
}
