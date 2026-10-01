// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.announcements

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.map
import social.aloha.core.model.Announcement
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.AnnouncementEndpoints
import social.aloha.core.network.endpoints.SafetyEndpoints

/**
 * What the reader's server announces to everyone on it: read here, marked read once seen, and
 * reacted to with an emoji.
 */
@Singleton
public class Announcements @Inject constructor(private val clients: ClientFactory) {
    // reader/announcement marked read here: shown read at once, while the server is still being told
    private val readHere = ConcurrentHashMap.newKeySet<String>()

    /** The announcements running now, newest first; one marked read here is read, whatever came back. */
    public suspend fun all(reader: SignedInAccount): Answer<List<Announcement>> =
        clients.answer(reader, SafetyEndpoints.announcements()).map { list ->
            list.map { if ("${reader.id}/${it.id}" in readHere) it.copy(read = true) else it }
                .sortedByDescending { it.publishedAt }
        }

    public suspend fun read(reader: SignedInAccount, id: String): Answer<Unit> {
        readHere += "${reader.id}/$id"
        return clients.answer(reader, AnnouncementEndpoints.dismiss(id))
    }

    /** Adds the reader's reaction [name], or takes it back unless [add]. */
    public suspend fun react(reader: SignedInAccount, id: String, name: String, add: Boolean): Answer<Unit> =
        clients.answer(
            reader,
            if (add) AnnouncementEndpoints.react(id, name) else AnnouncementEndpoints.unreact(id, name),
        )
}
