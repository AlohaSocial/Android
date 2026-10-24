// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.Answer
import social.aloha.core.data.explore.Explore
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.StarterPack

/**
 * A starter pack opened: [pack] as listed, shown at once, and [members] once the server read them.
 * [pending] are the members being followed one at a time, and [followingAll] whether the whole pack is
 * being followed.
 */
@Immutable
internal data class PackSheet(
    val pack: StarterPack,
    val members: Load<StarterPack> = Load.Waiting,
    val pending: Set<String> = emptySet(),
    val followingAll: Boolean = false,
)

/**
 * Explore's starter packs, as [reader]: one opened in its sheet with its members and whom of them the
 * reader follows, a member followed on their own, and a pack followed whole, from its sheet or its row.
 */
internal class PackSheets(
    private val state: MutableStateFlow<ExploreUiState>,
    private val scope: CoroutineScope,
    private val explore: Explore,
    private val profiles: ProfileRepository,
    private val follows: FollowedAuthors,
    private val reader: () -> SignedInAccount?,
) {
    fun open(pack: StarterPack) {
        state.update { it.copy(sheet = PackSheet(pack)) }
        load(pack.slug)
    }

    fun close() = state.update { it.copy(sheet = null) }

    fun retry() {
        val slug = state.value.sheet?.pack?.slug ?: return
        sheet(slug) { it.copy(members = Load.Waiting) }
        load(slug)
    }

    /** Follows [member] of the open pack, as following from their profile would. */
    fun followMember(member: Account) {
        val account = reader() ?: return
        val slug = state.value.sheet?.pack?.slug ?: return
        sheet(slug) { it.copy(pending = it.pending + member.id) }
        scope.launch {
            val answer = profiles.change(account, member.id, RelationshipChange.Follow())
            sheet(slug) { it.copy(pending = it.pending - member.id) }
            if (answer is Answer.Missed) state.update { it.copy(memberFailed = member.bestDisplayName) }
        }
    }

    fun followAll(slug: String) {
        val account = reader() ?: return
        sheet(slug) { it.copy(followingAll = true) }
        scope.launch {
            val done = explore.followAll(account, slug) == Answer.Got(true)
            sheet(slug) { it.copy(followingAll = false) }
            if (done) {
                state.update { it.copy(followed = it.followed + slug) }
                // the server names those followed by handle, not id: whom the open pack's members are followed
                // by now is asked again
                val open = (state.value.sheet?.members as? Load.Loaded)?.value?.takeIf { it.slug == slug }
                open?.let { follows.ask(account, members(it), again = true) }
            } else {
                state.update { it.copy(followFailed = true) }
            }
        }
    }

    private fun load(slug: String) {
        val account = reader() ?: return
        scope.launch {
            val answer = explore.pack(account, slug)
            sheet(slug) { it.copy(members = answer.load()) }
            // asked again each time it opens: a pack followed from its row, or with the sheet closed meanwhile,
            // has followed people without the app learning whom
            if (answer is Answer.Got) follows.ask(account, members(answer.value), again = true)
        }
    }

    // the server never has the reader follow themselves
    private fun members(pack: StarterPack): List<String> =
        pack.accounts.map { it.id }.filter { it != state.value.viewer }

    /** Changes the sheet while it shows [slug]; one closed or showing another pack meanwhile is left alone. */
    private fun sheet(slug: String, change: (PackSheet) -> PackSheet) = state.update { now ->
        val open = now.sheet?.takeIf { it.pack.slug == slug } ?: return@update now
        now.copy(sheet = change(open))
    }
}
