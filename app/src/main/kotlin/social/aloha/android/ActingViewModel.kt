// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.timeline.ActingAs
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.ui.ActAs
import social.aloha.core.ui.LocalActAs
import social.aloha.core.ui.PostAct

/** How an act as one of the reader's accounts went: whether it did, and as whom. */
internal data class Acted(val went: Boolean, val handle: String)

/**
 * Acting on a post as one of the reader's accounts, for every screen: the visibility a boost goes out by,
 * and how each act went, said once in [done]. The accounts to choose from are the switcher's.
 */
@HiltViewModel
internal class ActingViewModel @Inject constructor(
    private val accounts: AccountRepository,
    preferences: AppPreferences,
    private val acting: ActingAs,
) : ViewModel() {
    val defaultVisibility: StateFlow<Visibility> = preferences.writing.map { it.visibility ?: Visibility.Public }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Visibility.Public)

    private val outcomes = MutableSharedFlow<Acted>(extraBufferCapacity = 1)

    /** How each act went. */
    val done: SharedFlow<Acted> = outcomes.asSharedFlow()

    /** [act] on the post at [url] as [accountId]; a reply is the composer's, not this. */
    fun act(accountId: String, url: String, act: PostAct) {
        val toggle = when (act) {
            PostAct.Favourite -> Toggle.Favourite
            PostAct.Bookmark -> Toggle.Bookmark
            is PostAct.Boost -> Toggle.Boost
            PostAct.Reply -> return
        }
        viewModelScope.launch {
            val went = acting.toggle(accountId, url, toggle, (act as? PostAct.Boost)?.visibility) == null
            outcomes.emit(Acted(went, accounts.byId(accountId)?.qualifiedHandle.orEmpty()))
        }
    }
}

/** A new composer for [readerId], its draft named at once so it survives the app being stopped. */
internal fun composerFor(readerId: String, replyToUrl: String? = null): ComposerKey =
    ComposerKey(readerId, replyToUrl = replyToUrl, draftId = UUID.randomUUID().toString())

/** The root's acting, with a reply as another account opening that account's composer through [push]. */
@Composable
internal fun rememberShellActAs(push: (ComposerKey) -> Unit): ActAs? {
    val outer = LocalActAs.current
    val opening by rememberUpdatedState(push)
    return remember(outer) {
        outer?.let { acts ->
            object : ActAs by acts {
                override fun act(accountId: String, url: String, act: PostAct) = if (act == PostAct.Reply) {
                    opening(composerFor(accountId, replyToUrl = url))
                } else {
                    acts.act(accountId, url, act)
                }
            }
        }
    }
}
