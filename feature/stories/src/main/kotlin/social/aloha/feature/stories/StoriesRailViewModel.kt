// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.stories.Stories
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Story

/**
 * The active account's stories rail, empty where the server has no stories or nobody posted one, and
 * what the player does with a story: mark it seen, and for the reader's own, who watched and what they
 * sent back, or end it early; for anybody else's, an emoji or a reply to its poster.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class StoriesRailViewModel @Inject constructor(accounts: AccountRepository, private val stories: Stories) :
    ViewModel() {
    private val reader: StateFlow<SignedInAccount?> = accounts.activeAccount
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.capabilities?.stories == b?.capabilities?.stories }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val reloads = MutableStateFlow(0)

    val reels: StateFlow<List<StoryReel>> = combine(reader, reloads) { reader, _ -> reader }
        .mapLatest { reader ->
            val answer = reader?.takeIf { it.capabilities.stories }?.let { stories.rail(it) }
            (answer as? Answer.Got)?.value.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), emptyList())

    // the rail keeps its seen flags until it reloads, so a story watched twice is marked once
    private val marked = mutableSetOf<String>()

    val actions: StoryActions = object : StoryActions {
        override fun onSeen(story: Story) {
            if (story.seen || !marked.add(story.id)) return
            withReader { stories.markSeen(it, story.id) }
        }

        override suspend fun audience(id: String): Audience? {
            val reader = reader.value ?: return null
            val viewers = stories.viewers(reader, id) as? Answer.Got ?: return null
            // reactions are Pixelfed's own; a server without them still says who watched
            val reactions = (stories.reactions(reader, id) as? Answer.Got)?.value.orEmpty()
            return Audience(viewers.value, reactions)
        }

        override suspend fun react(id: String, reaction: String): Boolean =
            reader.value?.let { stories.react(it, id, reaction) } is Answer.Got

        override suspend fun reply(id: String, text: String): Boolean =
            reader.value?.let { stories.reply(it, id, text) } is Answer.Got

        override suspend fun delete(id: String): Boolean {
            val gone = reader.value?.let { stories.delete(it, id) } is Answer.Got
            if (gone) reloads.value++
            return gone
        }
    }

    private fun withReader(block: suspend (SignedInAccount) -> Unit) {
        val reader = reader.value ?: return
        viewModelScope.launch { block(reader) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
