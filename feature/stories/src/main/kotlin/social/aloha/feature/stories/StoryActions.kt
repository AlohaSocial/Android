// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import social.aloha.core.model.Account
import social.aloha.core.model.Story
import social.aloha.core.model.StoryReaction

/** Who watched one of the reader's own stories, and what they sent back. */
internal data class Audience(val viewers: List<Account>, val reactions: List<StoryReaction>)

/** What the story player asks of the server; each answers whether it went through. */
internal interface StoryActions {
    fun onSeen(story: Story)

    /** None where the server would not say. */
    suspend fun audience(id: String): Audience?

    suspend fun react(id: String, reaction: String): Boolean

    suspend fun reply(id: String, text: String): Boolean

    suspend fun delete(id: String): Boolean
}
