// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.Answer
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.saved.PostPage
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusRowUi

/** The photos others tagged an account in, as read so far: none until asked, [done] at the end. */
internal data class TaggedFound(
    val posts: List<Status>? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val done: Boolean = false,
    val viewer: String = "",
    /** How taking the reader's name off a photo went, until said: true once off, false where refused. */
    val untagged: Boolean? = null,
)

/**
 * The Tagged tab as shown: its rows, null until the first page answered; [own] where they are photos the
 * reader is tagged in, who can take their name off each.
 */
@Immutable
internal data class TaggedUi(
    val rows: List<StatusRowUi>? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val done: Boolean = false,
    val own: Boolean = false,
    val untagged: Boolean? = null,
)

/**
 * The Tagged tab's state and what it does: [onMore] reads the next page, or the first again after a
 * failure; [onUntag] takes the reader's name off a photo.
 */
internal data class TaggedPosts(val state: TaggedUi, val onMore: () -> Unit, val onUntag: (String) -> Unit)

/**
 * The photos others tagged one account in, a page at a time as the reader asks (Pixelfed's `tagged`, as
 * Aloha Social serves it); a page that failed keeps what was read before.
 */
internal class ProfileTagged(private val profiles: ProfileRepository, private val scope: CoroutineScope) {
    private val shown = MutableStateFlow(TaggedFound())
    val found: StateFlow<TaggedFound> = shown.asStateFlow()
    private var last: PostPage? = null
    private var job: Job? = null

    /** The first page, unless it was read already; [fresh] reads it again. */
    fun load(reader: SignedInAccount, accountId: String, fresh: Boolean = false) {
        if (!fresh && shown.value.posts != null) return
        job?.cancel()
        last = null
        shown.value = TaggedFound(loading = true, viewer = reader.serverAccountId)
        job = scope.launch { page(reader, accountId) }
    }

    /** The next page, unless one is on its way or there is none. */
    fun more(reader: SignedInAccount, accountId: String) {
        val now = shown.value
        if (now.loading || now.done) return
        shown.update { it.copy(loading = true, failed = false) }
        job = scope.launch { page(reader, accountId) }
    }

    /**
     * Takes the reader's name off photo [statusId]: off the tab at once, and back where it was if the
     * server refuses.
     */
    fun untag(reader: SignedInAccount, statusId: String) {
        val shownPosts = shown.value.posts ?: return
        val index = shownPosts.indexOfFirst { it.id == statusId }.takeIf { it >= 0 } ?: return
        val post = shownPosts[index]
        shown.update { it.copy(posts = it.posts?.filter { kept -> kept.id != statusId }) }
        scope.launch {
            val off = profiles.untagMe(reader, post.id) is Answer.Got
            shown.update { now ->
                val back = now.posts.orEmpty().toMutableList().apply { if (!off) add(index.coerceAtMost(size), post) }
                now.copy(posts = back, untagged = off)
            }
        }
    }

    /** Says how the last untagging went only once. */
    fun untagShown() = shown.update { it.copy(untagged = null) }

    private suspend fun page(reader: SignedInAccount, accountId: String) {
        when (val answer = profiles.tagged(reader, accountId, last)) {
            is Answer.Got -> {
                last = answer.value
                shown.update {
                    it.copy(
                        posts = (it.posts.orEmpty() + answer.value.posts).distinctBy(Status::id),
                        loading = false,
                        done = answer.value.done,
                    )
                }
            }

            is Answer.Missed -> shown.update { it.copy(loading = false, failed = true) }
        }
    }
}

/** The Tagged tab's rows: the photos, a way to more of them, or why there are none. */
internal fun LazyListScope.taggedRows(tagged: TaggedPosts, now: Instant, rowActions: StatusActions) {
    val state = tagged.state
    val rows = state.rows
    when {
        rows == null && state.failed -> item(key = "tagged-failed") { Failed(tagged.onMore) }

        rows == null -> item(key = "tagged-loading") { ListProgress() }

        rows.isEmpty() && !state.loading -> item(key = "tagged-none") {
            Text(
                stringResource(R.string.profile_tagged_none),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.l),
            )
        }

        else -> {
            items(rows, key = { "tagged-${it.rowId}" }, contentType = { "post" }) { row ->
                StatusCard(row, now, LocalSensitiveMediaPolicy.current, rowActions)
                if (state.own) RemoveMe(row) { tagged.onUntag(row.statusId) }
                PostDivider()
            }
            when {
                state.loading -> item(key = "tagged-more") { ListProgress() }

                state.failed -> item(key = "tagged-more") { Failed(tagged.onMore) }

                !state.done -> item(key = "tagged-more") {
                    TextButton(onClick = tagged.onMore, modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.s)) {
                        Text(stringResource(R.string.profile_tagged_more))
                    }
                }
            }
        }
    }
}

/** Takes the reader's name off [row]'s photo, as the web app's "Remove me" does. */
@Composable
private fun RemoveMe(row: StatusRowUi, onRemove: () -> Unit) {
    // whose photo, said aloud: every row has the button
    val label = stringResource(R.string.profile_tagged_remove_me_from, row.author.plainName)
    TextButton(
        onClick = onRemove,
        modifier = Modifier.padding(horizontal = AlohaSpacing.s).semantics { contentDescription = label },
    ) { Text(stringResource(R.string.profile_tagged_remove_me)) }
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.profile_tagged_failed), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.profile_retry)) }
    }
}
