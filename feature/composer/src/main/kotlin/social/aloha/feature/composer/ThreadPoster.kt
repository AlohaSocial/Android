// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import java.util.UUID
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.trouble
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Visibility
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.StatusPost

/** One segment of a thread as the writer left it, before its games are played. */
internal data class Segment(
    val text: String,
    val spoiler: String?,
    val visibility: Visibility,
    val language: String?,
    val quotePolicy: QuotePolicy,
    val mediaIds: List<String> = emptyList(),
    /** The media warn on their own, text or no text. */
    val mediaSensitive: Boolean = false,
)

/**
 * Posts a thread, segment by segment, each answering the one before, and stops at the first the
 * server does not take; the segments already posted stay posted, and sending again carries on from
 * the one that failed. A segment keeps one idempotency key, and the result of every game it played,
 * for as long as it stays as written: sending again after a timeout carries the same key, so the
 * server answers with the post it already made rather than making a second, and a die rolled once
 * stays rolled. Any change to the segment mints a new key and plays its games afresh.
 */
internal class ThreadPoster(
    private val compose: ComposeRepository,
    private val words: ComposerGames.Words,
    private val random: () -> Double = Math::random,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) {
    private class Minted(val segment: Segment, val key: String, val text: String)

    private val minted = HashMap<Int, Minted>()
    private val postedIds = mutableListOf<String>()

    /** How many segments are posted. */
    val posted: Int get() = postedIds.size

    /**
     * Sends the segments not yet posted as [account], the first answering [inReplyToId]; [onPosted]
     * hears of each one the server took. The failure that stopped it, or null when all are posted.
     */
    suspend fun send(
        account: SignedInAccount,
        segments: List<Segment>,
        inReplyToId: String?,
        onPosted: (Int) -> Unit,
    ): PostFailure? {
        var answering = postedIds.lastOrNull() ?: inReplyToId
        for (index in posted..segments.lastIndex) {
            when (val answer = compose.post(account, post(index, segments[index], answering))) {
                is Answer.Got -> {
                    postedIds += answer.value.id
                    answering = answer.value.id
                    onPosted(posted)
                }

                is Answer.Missed -> return failureOf(answer.error)
            }
        }
        return null
    }

    /** Forgets the segments from [index] on, which were removed; posted ones are never forgotten. */
    fun forgetFrom(index: Int) {
        minted.keys.removeAll { it >= maxOf(index, posted) }
    }

    private fun post(index: Int, segment: Segment, inReplyToId: String?): StatusPost {
        val current = minted[index]?.takeIf { it.segment == segment }
            ?: Minted(segment, newKey(), ComposerGames.play(segment.text, words, random)).also { minted[index] = it }
        return StatusPost(
            text = current.text,
            visibility = segment.visibility,
            spoilerText = segment.spoiler,
            // a content warning hides the text, so the post is sensitive; media may be on their own
            sensitive = segment.spoiler != null || (segment.mediaSensitive && segment.mediaIds.isNotEmpty()),
            mediaIds = segment.mediaIds,
            language = segment.language,
            inReplyToId = inReplyToId,
            idempotencyKey = current.key,
            quotePolicy = segment.quotePolicy.wire,
        )
    }

    private fun failureOf(error: ApiError): PostFailure = when (error) {
        is ApiError.Unprocessable -> PostFailure.Refused(error.message)
        else -> PostFailure.Unreached(error.trouble)
    }
}
