// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.tags

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Tag
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.PageAnchor
import social.aloha.core.network.endpoints.TagEndpoints
import social.aloha.core.network.endpoints.TimelineEndpoints

/** Several hashtags read as one timeline, the reader's own, kept on the device and never on the server. */
public data class TagGroup(val name: String, val tags: List<String>)

/**
 * Hashtags: one looked up, followed and unfollowed, those followed, and those that travel with it
 * (Nextcloud Social). A name is normalised as the server stores it, so `#NextCloud` and `nextcloud`
 * are one tag; one that normalises to nothing is not a hashtag, said before the server is asked.
 * Tag groups are the reader's own: several hashtags whose posts are merged into one timeline here.
 */
@Singleton
public class Hashtags @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
    private val settings: AccountSettingsStore,
) {
    public suspend fun tag(reader: SignedInAccount, name: String): Answer<Tag> =
        named(name) { clients.answer(reader, TagEndpoints.tag(it)) }

    public suspend fun follow(reader: SignedInAccount, name: String, follow: Boolean): Answer<Tag> = named(name) {
        clients.answer(reader, if (follow) TagEndpoints.follow(it) else TagEndpoints.unfollow(it))
    }

    public suspend fun followed(reader: SignedInAccount): Answer<List<Tag>> =
        clients.answer(reader, TagEndpoints.followed())

    /** The tags used with [name] on public posts; none where the server has no such route. */
    public suspend fun related(reader: SignedInAccount, name: String): List<Tag> =
        (clients.answer(reader, TagEndpoints.related(name)) as? Answer.Got)?.value.orEmpty()

    public fun groups(reader: SignedInAccount): Flow<List<TagGroup>> = settings.settings(reader.id).map { stored ->
        stored.tagGroups.map { (name, tags) -> TagGroup(name, tags) }.sortedBy { it.name.lowercase() }
    }

    /**
     * Keeps group [name] with [tags], normalised, once each, in place of group [previous] when one is
     * being changed; a group of none is not kept. False, and nothing changed, for a blank name or one
     * another group already has: saving must never quietly replace someone's other group.
     */
    public suspend fun saveGroup(
        reader: SignedInAccount,
        name: String,
        tags: List<String>,
        previous: String? = null,
    ): Boolean {
        val wanted = name.trim()
        val kept = tags.mapNotNull(Tag::normalise).distinct()
        var saved = false
        settings.update(reader.id) { current ->
            val taken = wanted != previous && wanted in current.tagGroups
            if (wanted.isEmpty() || taken) return@update current
            saved = true
            val others = current.tagGroups - setOfNotNull(previous)
            current.copy(tagGroups = if (kept.isEmpty()) others else others + (wanted to kept))
        }
        return saved
    }

    public suspend fun deleteGroup(reader: SignedInAccount, name: String) {
        settings.update(reader.id) { it.copy(tagGroups = it.tagGroups - name) }
    }

    /**
     * The next page of [tags]' posts together, newest first, each post once: every tag still open is asked
     * for its next page at once, from where [after] left it. Tags page through time at their own pace, so
     * only posts newer than the oldest one any open tag has reached are shown; the rest wait for the next
     * page, so a later page never lands above what was shown. A tag whose page fails stays open; all of
     * them failing is the first failure.
     */
    public suspend fun groupPage(
        reader: SignedInAccount,
        tags: List<String>,
        after: GroupCursor = GroupCursor(),
    ): Answer<GroupPage> = coroutineScope {
        val open = tags.filterNot { it in after.exhausted }
        if (open.isEmpty()) return@coroutineScope Answer.Got(GroupPage(after.held, GroupCursor(), done = true))
        val answers = open.map { tag ->
            async {
                val anchor = after.older[tag]?.let(PageAnchor::OlderThan) ?: PageAnchor.Cold
                tag to clients.answer(reader, TimelineEndpoints.timeline(TimelineSource.Hashtag(tag), anchor = anchor))
            }
        }.awaitAll()
        val pages = answers.mapNotNull { (tag, answer) -> (answer as? Answer.Got)?.value?.let { tag to it } }
        if (pages.isEmpty()) return@coroutineScope answers.first().second as Answer.Missed
        statuses.saveAll(reader.id, pages.flatMap { it.second })
        Answer.Got(merged(after, open, pages, failed = pages.size < answers.size))
    }

    private fun merged(
        after: GroupCursor,
        open: List<String>,
        pages: List<Pair<String, List<Status>>>,
        failed: Boolean,
    ): GroupPage {
        val exhausted = after.exhausted + pages.filter { it.second.isEmpty() }.map { it.first }
        val older = after.older + pages.mapNotNull { (tag, page) -> page.lastOrNull()?.let { tag to it.id } }
        val reached = after.reached + pages.mapNotNull { (tag, page) -> page.lastOrNull()?.let { tag to it.createdAt } }
        val pool = (after.held + pages.flatMap { it.second }).distinctBy { it.id }.sortedByDescending { it.createdAt }
        // the oldest moment an open tag has reached: anything newer is complete, anything older may still move
        val frontier = reached.filterKeys { it !in exhausted }.values.maxOrNull()
        val (shown, held) = pool.partition { frontier == null || !it.createdAt.isBefore(frontier) }
        val cursor = GroupCursor(older, reached, exhausted, held)
        return GroupPage(shown, cursor, done = !failed && held.isEmpty() && open.all { it in exhausted })
    }

    /** [ask] for [name] normalised; one that normalises to nothing is refused without asking. */
    private suspend fun <T> named(name: String, ask: suspend (String) -> Answer<T>): Answer<T> {
        val tag = Tag.normalise(name) ?: return Answer.Missed(ApiError.Unprocessable(null))
        return ask(tag)
    }
}

/** A tag group's posts, a page at a time; [cursor] where the next page starts. */
public data class GroupPage(val posts: List<Status>, val cursor: GroupCursor, val done: Boolean)

/**
 * Where a tag group's next page starts: each tag's last post id and time, the tags that ran out, and the
 * posts fetched but not shown yet, since an open tag may still have newer ones.
 */
public data class GroupCursor(
    val older: Map<String, String> = emptyMap(),
    val reached: Map<String, Instant> = emptyMap(),
    val exhausted: Set<String> = emptySet(),
    val held: List<Status> = emptyList(),
)
