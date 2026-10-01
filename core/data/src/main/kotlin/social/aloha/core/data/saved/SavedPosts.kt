// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.saved

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.PageAnchor
import social.aloha.core.network.endpoints.Paging
import social.aloha.core.network.endpoints.StatusExtraEndpoints
import social.aloha.core.network.endpoints.TimelineEndpoints

/** A page of posts; [next] is the server's cursor to the one after it, where it gave one. */
public data class PostPage(val posts: List<Status>, val next: HttpUrl?, val done: Boolean)

/**
 * The posts the reader kept: bookmarked, favourited, or archived off their profile (Nextcloud Social's
 * archive, at Pixelfed's paths). Bookmarks and favourites are in the order they were kept, which is
 * no post's id, so they page along the server's `Link` header; the archive sends none, and pages
 * from its oldest post's id.
 */
@Singleton
public class SavedPosts @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
) {
    public suspend fun bookmarks(reader: SignedInAccount, after: PostPage? = null): Answer<PostPage> =
        page(reader, after) { TimelineEndpoints.timeline(TimelineSource.Bookmarks, anchor = it) }

    public suspend fun favourites(reader: SignedInAccount, after: PostPage? = null): Answer<PostPage> =
        page(reader, after) { TimelineEndpoints.timeline(TimelineSource.Favourites, anchor = it) }

    public suspend fun archived(reader: SignedInAccount, after: PostPage? = null): Answer<PostPage> =
        page(reader, after, byPostId = true) { StatusExtraEndpoints.archived(anchor = it) }

    /** Puts the reader's archived post back on their profile. */
    public suspend fun unarchive(reader: SignedInAccount, id: String): Answer<Unit> =
        clients.answer(reader, StatusExtraEndpoints.unarchive(id))

    /**
     * The page after [after]. Only a list ordered by post id, the archive, can page on from its oldest
     * post; bookmarks and favourites are ordered by when they were kept, so without the server's cursor
     * there is no next page to ask for.
     */
    private suspend fun page(
        reader: SignedInAccount,
        after: PostPage?,
        byPostId: Boolean = false,
        request: (PageAnchor) -> ApiRequest<List<Status>>,
    ): Answer<PostPage> {
        val client = clients.forAccount(reader) ?: return Answer.Missed(ApiError.NotFound)
        val asked = request(after?.posts?.lastOrNull()?.id?.let(PageAnchor::OlderThan) ?: PageAnchor.Cold)
        val result = after?.next?.let { client.page(it, asked, Paging.DEFAULT_LIMIT) }
            ?: client.page(asked, Paging.DEFAULT_LIMIT)
        return when (result) {
            is ApiResult.Success -> {
                val page = result.value
                // stored, so a tap opens the post at once
                statuses.saveAll(reader.id, page.items)
                val done = !page.mayHaveMore || (!byPostId && page.link.next == null)
                Answer.Got(PostPage(page.items, page.link.next, done))
            }

            is ApiResult.Failure -> Answer.Missed(result.error)
        }
    }
}
