// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.photos

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.CollectionDraft
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.network.endpoints.CollectionEndpoints

/**
 * Albums: named sets of posts with pictures, Pixelfed's collections, which Nextcloud Social serves too.
 * The server holds them; nothing is kept here but the posts they show, stored like any others so a tap
 * opens them at once.
 */
@Singleton
public class Albums @Inject constructor(private val clients: ClientFactory, private val statuses: StatusRepository) {
    /** [reader]'s own albums. */
    public suspend fun own(reader: SignedInAccount): Answer<List<MediaCollection>> =
        clients.answer(reader, CollectionEndpoints.all())

    /** The albums of account [ownerId], as [reader] may see them. */
    public suspend fun of(reader: SignedInAccount, ownerId: String): Answer<List<MediaCollection>> =
        clients.answer(reader, CollectionEndpoints.forAccount(ownerId))

    /** The posts in album [id], newest first as the server keeps them. */
    public suspend fun posts(reader: SignedInAccount, id: String): Answer<List<Status>> {
        val answer = clients.answer(reader, CollectionEndpoints.items(id))
        if (answer is Answer.Got) statuses.saveAll(reader.id, answer.value)
        return answer
    }

    public suspend fun create(reader: SignedInAccount, title: String, description: String): Answer<MediaCollection> =
        clients.answer(reader, CollectionEndpoints.create(CollectionDraft(title.trim(), description.trim())))

    /** [album] under a new [title], its description and visibility as they were. */
    public suspend fun rename(reader: SignedInAccount, album: MediaCollection, title: String): Answer<MediaCollection> {
        val draft = CollectionDraft(title.trim(), album.description.orEmpty(), album.visibility ?: PUBLIC)
        return clients.answer(reader, CollectionEndpoints.update(album.id, draft))
    }

    public suspend fun delete(reader: SignedInAccount, id: String): Answer<Unit> =
        clients.answer(reader, CollectionEndpoints.delete(id))

    public suspend fun add(reader: SignedInAccount, id: String, statusId: String): Answer<Unit> =
        clients.answer(reader, CollectionEndpoints.addItem(id, statusId))

    public suspend fun remove(reader: SignedInAccount, id: String, statusId: String): Answer<Unit> =
        clients.answer(reader, CollectionEndpoints.removeItem(id, statusId))

    private companion object {
        const val PUBLIC = "public"
    }
}
