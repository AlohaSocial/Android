// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.map
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError

/** How far sending got: the post with the segments now out, and the failure that stopped it, if one did. */
public data class Sent(val post: DraftPost, val error: ApiError?) {
    /**
     * The segments after the one sending stopped at, none of them sent, as a new thread to keep as a
     * draft: answering nothing, with no poll, quote or time of the opening post, unkeyed, and with
     * their media as they are, uploaded or not but attached to no post; null when there are none.
     */
    public val rest: DraftPost?
        get() = post.segments.drop(post.postedIds.size + 1).takeIf { it.isNotEmpty() }?.let { segments ->
            DraftPost(
                segments = segments.map { it.copy(sent = null, key = null) },
                spoiler = post.spoiler,
                visibility = post.visibility,
                language = post.language,
                mediaSensitive = post.mediaSensitive,
            )
        }
}

/**
 * Sends a post, segment by segment, each answering the one before, and stops at the first the server
 * does not take; what went out stays out, and sending the post it hands back carries on from there.
 * A segment a review rule holds ([ApiError.heldForReview]), scheduled or not, stops it too: nothing
 * can answer a post that is not out, so the segments after it are not sent ([Sent.rest]), and the
 * original of a post written again stays up.
 * The composer and the outbox's worker send through this one path.
 */
@Singleton
public class PostSender @Inject constructor(
    private val compose: ComposeRepository,
    private val scheduled: ScheduledPosts,
    private val interactions: StatusInteractions,
) {
    /**
     * Sends the segments of [post] after its [DraftPost.postedIds] as [reader]; [onProgress] hears of
     * the post as it changes, keyed before the first request and after each segment the server took,
     * so it can be kept for sending again.
     */
    public suspend fun send(reader: SignedInAccount, post: DraftPost, onProgress: suspend (DraftPost) -> Unit): Sent {
        var current = keyed(post)
        if (current != post) onProgress(current)
        for (index in current.postedIds.size..current.segments.lastIndex) {
            val status = current.statusPost(index, current.postedIds.lastOrNull() ?: current.replyToId)
            // a scheduled post is one alone: nothing can answer a post that is not there yet
            val made = if (status.scheduledAt != null) {
                scheduled.schedule(reader, status).map { it.id }
            } else {
                compose.post(reader, status).map { it.id }
            }
            when (made) {
                is Answer.Got -> {
                    current = current.copy(postedIds = current.postedIds + made.value)
                    onProgress(current)
                }

                is Answer.Missed -> return Sent(current, made.error)
            }
        }
        // a post written again replaces its original only once it is out: until then, nothing is lost.
        // ponytail: a server that keeps the original's media from the new post refuses it with a 422
        current.replaces?.let { interactions.deleted(reader, it) }
        return Sent(current, null)
    }

    private fun keyed(post: DraftPost): DraftPost = post.copy(
        segments = post.segments.map { if (it.key == null) it.copy(key = UUID.randomUUID().toString()) else it },
    )
}
