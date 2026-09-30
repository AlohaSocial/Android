// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import java.time.Duration
import java.time.Instant
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.OutboxEntry
import social.aloha.core.data.compose.draftOf
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.ComposerKey

/** What the composer opens on. */
internal sealed interface Opened {
    /**
     * A post to write: a new one when [post] is null, else one carried on with, from the outbox
     * ([entry]), or a post of the writer's own being [editing] or written again.
     */
    data class Writing(val post: DraftPost?, val entry: OutboxEntry? = null, val editing: String? = null) : Opened

    /** The post is going out right now and cannot be changed. */
    data object Sending : Opened

    /** The post to edit or write again could not be had from the server. */
    data object Gone : Opened
}

/**
 * Finds what the composer opens on: a draft, one of the writer's own posts to edit (as they wrote it,
 * from `/source`), or one to write again, as a new post that replaces it once it is out.
 */
internal class PostOpener(
    private val compose: ComposeRepository,
    private val interactions: StatusInteractions,
    private val drafts: DraftKeeper,
    private val now: () -> Instant = Instant::now,
) {
    /**
     * A draft kept under the key comes first: a composer restored after the app was stopped carries
     * on with what was written, rather than editing or deleting the post again.
     */
    suspend fun open(account: SignedInAccount, key: ComposerKey): Opened {
        val kept = drafts.open(account.id)
        if (kept !is Opened.Writing || kept.entry != null) return kept
        return key.editId?.let { edit(account, it) } ?: key.redraftId?.let { redraft(account, it) } ?: kept
    }

    private suspend fun edit(account: SignedInAccount, id: String): Opened {
        val status = (compose.status(account, id) as? Answer.Got)?.value
        val source = (interactions.source(account, id) as? Answer.Got)?.value
        if (status == null || source == null) return Opened.Gone
        val post = draftOf(status, source.text, source.spoilerText)
        // a poll sent again keeps its end: an edit restarts its clock from the moment it is saved
        val left = status.poll?.expiresAt?.let { Duration.between(now(), it).seconds.coerceAtLeast(MIN_POLL_SECONDS) }
        val poll = post.poll?.let { it.copy(seconds = left ?: it.seconds) }
        return Opened.Writing(post.copy(poll = poll), editing = id)
    }

    /**
     * The post written again: its source and media, as a new post that replaces it. The original is
     * deleted only once the new one is out, so a writer who gives up, or a post that fails, loses
     * nothing.
     */
    private suspend fun redraft(account: SignedInAccount, id: String): Opened {
        val status = (compose.status(account, id) as? Answer.Got)?.value ?: return Opened.Gone
        val source = (interactions.source(account, id) as? Answer.Got)?.value
        val text = source?.text ?: status.text ?: StatusHtmlParser.plainText(status.content)
        return Opened.Writing(draftOf(status, text, source?.spoilerText ?: status.spoilerText).copy(replaces = id))
    }

    private companion object {
        const val MIN_POLL_SECONDS = 300L
    }
}
