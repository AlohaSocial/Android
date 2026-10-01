// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import java.util.UUID
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.PostSender
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.trouble
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError

/**
 * Posts a thread through [PostSender], which stops at the first segment the server does not take;
 * sending again carries on from the one that failed. A segment keeps one idempotency key, and the
 * result of every game it played, for as long as it and the post stay as written: sending again
 * after a timeout carries the same key, so the server answers with the post it already made rather
 * than making a second, and a die rolled once stays rolled. Any change mints a new key and plays its
 * games afresh.
 */
internal class ThreadPoster(
    private val sender: PostSender,
    private val interactions: StatusInteractions,
    private val words: ComposerGames.Words,
    private val random: () -> Double = Math::random,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) {
    private class Minted(val written: Any, val key: String, val text: String)

    private val minted = HashMap<Int, Minted>()

    /** The segments already out, which sending again starts after. */
    var postedIds: List<String> = emptyList()

    /** How many segments are posted. */
    val posted: Int get() = postedIds.size

    /** Sending began: from now on the post is kept with its keys, whatever became of the attempt. */
    private var attempted = false

    /**
     * [post] as a draft keeps it: once sending began, with the keys the server may already know and
     * the segments already out, so sending it again later never makes a post twice.
     */
    fun keep(post: DraftPost): DraftPost = if (attempted) prepare(post) else post

    /** Carries on with [post], a draft kept after sending began: its keys, games and posted segments. */
    fun adopt(post: DraftPost) {
        postedIds = post.postedIds
        attempted = post.segments.any { it.key != null }
        val shared = post.copy(segments = emptyList(), postedIds = emptyList())
        post.segments.forEachIndexed { index, segment ->
            val key = segment.key ?: return@forEachIndexed
            minted[index] = Minted(shared to segment.copy(sent = null, key = null), key, segment.sent ?: segment.text)
        }
    }

    /** [post] as it goes out: each segment keyed and its games played, the ones already out marked. */
    fun prepare(post: DraftPost): DraftPost {
        val shared = post.copy(segments = emptyList(), postedIds = emptyList())
        return post.copy(
            postedIds = postedIds,
            segments = post.segments.mapIndexed { index, segment ->
                val written = shared to segment.copy(sent = null, key = null)
                val current = minted[index]?.takeIf { it.written == written }
                    ?: Minted(written, newKey(), ComposerGames.play(segment.text, words, random))
                        .also { minted[index] = it }
                segment.copy(sent = current.text, key = current.key)
            },
        )
    }

    /**
     * Sends the segments of [post] not yet out as [account]; [onPosted] hears how many are out after
     * each one the server took. The failure that stopped it, or null when all are posted.
     */
    suspend fun send(
        account: SignedInAccount,
        post: DraftPost,
        editing: String?,
        onPosted: (Int) -> Unit,
    ): PostFailure? {
        attempted = true
        // an edit replaces the one post, as it is, carrying its media's descriptions
        if (editing != null) {
            val edited = interactions.edit(
                account,
                editing,
                prepare(post).statusPost(0, post.replyToId, editing = true),
            )
            return (edited as? Answer.Missed)?.error?.let(::failureOf)
        }
        val sent = sender.send(account, prepare(post)) { progress ->
            if (progress.postedIds.size > posted) {
                postedIds = progress.postedIds
                onPosted(posted)
            }
        }
        return sent.error?.let(::failureOf)
    }

    /** Forgets the segments from [index] on, which were removed; posted ones are never forgotten. */
    fun forgetFrom(index: Int) {
        minted.keys.removeAll { it >= maxOf(index, posted) }
    }
}

/** What a server's refusal means to the writer: a reason to fix, or a server to try again. */
internal fun failureOf(error: ApiError): PostFailure = when (error) {
    is ApiError.Unprocessable -> PostFailure.Refused(error.message)
    else -> PostFailure.Unreached(error.trouble)
}
