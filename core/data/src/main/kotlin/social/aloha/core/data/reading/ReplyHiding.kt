// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.reading

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.FeedPreferenceEndpoints

/**
 * Whether each account's Nextcloud Social leaves replies out of its home, public and hashtag feeds, a
 * switch the server keeps per account (`/api/v1/social/feed_preferences`, Social after 0.26.278) and
 * the web app turns too. Replies stay in their conversations either way.
 */
@Singleton
public class ReplyHiding @Inject constructor(
    private val clients: ClientFactory,
    private val timelines: TimelineRepository,
) {
    /** The switch as [reader]'s server holds it; null where the server has none (404) or did not answer. */
    public suspend fun hidden(reader: SignedInAccount): Boolean? =
        (clients.answer(reader, FeedPreferenceEndpoints.hideReplies()) as? Answer.Got)?.value

    /**
     * Turns the switch on [reader]'s server and answers with the state it then holds; null when it did
     * not take the change. A change taken drops the feeds it narrows, so they load again as it says.
     */
    public suspend fun hide(reader: SignedInAccount, hide: Boolean): Boolean? {
        val held = (clients.answer(reader, FeedPreferenceEndpoints.setHideReplies(hide)) as? Answer.Got)?.value
        // the server has the change, so the feeds go even if whoever asked is gone meanwhile
        if (held != null) withContext(NonCancellable) { timelines.dropFeeds(reader) }
        return held
    }
}
