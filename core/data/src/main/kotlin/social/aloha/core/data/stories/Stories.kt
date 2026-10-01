// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.stories

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.map
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Story
import social.aloha.core.network.endpoints.StoryEndpoints

/**
 * One poster's live stories on the rail, in the order to play them; [unseen] while any of them is.
 * [own] for the reader's own, which lead the rail.
 */
public data class StoryReel(val account: Account, val stories: List<Story>, val own: Boolean) {
    val unseen: Boolean get() = stories.any { !it.seen }
}

/**
 * Stories, Pixelfed's pictures and clips that are gone after a day, where the server serves them. The
 * rail is the server's carousel as it ranked it, grouped by poster, the reader's own first; a story
 * past its day is never shown, whatever the server still sends.
 */
@Singleton
public class Stories @Inject constructor(private val clients: ClientFactory, private val clock: Clock) {
    public suspend fun rail(reader: SignedInAccount): Answer<List<StoryReel>> =
        clients.answer(reader, StoryEndpoints.carousel()).map { carousel ->
            val now = clock.instant()
            val own = carousel.own.filter { it.isLive(now) }
            // a flat carousel mixes the reader's own in with everyone else's
            val (mine, theirs) = carousel.others.filter { it.isLive(now) }
                .partition { it.account?.id == reader.serverAccountId }
            reels(own + mine, own = true) + reels(theirs, own = false)
        }

    /** The stories with a known poster, one reel per poster, in the order their first story came. */
    private fun reels(stories: List<Story>, own: Boolean): List<StoryReel> = stories
        .mapNotNull { story -> story.account?.let { it to story } }
        .groupBy({ it.first.id }) { it }
        .map { (_, theirs) -> StoryReel(theirs.first().first, theirs.map { it.second }, own) }
}
