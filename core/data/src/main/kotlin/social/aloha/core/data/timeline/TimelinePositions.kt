// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.ClientFactory
import social.aloha.core.database.PositionDao
import social.aloha.core.database.TimelinePositionEntity
import social.aloha.core.model.ServerIds
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.MarkerEndpoints

/** Where a person left a timeline: the row at the top and how far past its top edge they had scrolled. */
public data class TimelinePosition(val statusId: String, val offset: Int)

/**
 * Where a person was. This device remembers each timeline's position; the home timeline's read marker
 * on the server says where they were on another device, and is where a fresh install starts. The
 * Apple app never wired either.
 */
@Singleton
public class TimelinePositions @Inject constructor(private val dao: PositionDao, private val clients: ClientFactory) {
    private val written = ConcurrentHashMap<String, String>()

    public suspend fun saved(account: SignedInAccount, key: TimelineKey): TimelinePosition? =
        dao.get(account.id, key.storageKey)?.let { TimelinePosition(it.statusId, it.offset) }

    public suspend fun save(account: SignedInAccount, key: TimelineKey, position: TimelinePosition) {
        dao.set(TimelinePositionEntity(account.id, key.storageKey, position.statusId, position.offset))
    }

    /** The newest home post read, on this device or another; null where neither says. */
    public suspend fun lastRead(account: SignedInAccount): String? =
        ServerIds.newest(listOfNotNull(saved(account, TimelineKey.home())?.statusId, homeMarker(account)))

    /** The newest home post read on any device, or null where the server keeps no marker. */
    public suspend fun homeMarker(account: SignedInAccount): String? =
        (clients.forAccount(account)?.execute(MarkerEndpoints.read()) as? ApiResult.Success)?.value?.home?.lastReadId

    /**
     * Moves the home read marker up to [statusId], never back: a device that is behind must not un-read
     * what another has read. Written once per new high point, so scrolling does not send a request a row.
     */
    public suspend fun markHomeRead(account: SignedInAccount, statusId: String) {
        val previous = written[account.id]
        if (previous != null && !isNewer(statusId, previous)) return
        written[account.id] = statusId
        clients.forAccount(account)?.execute(MarkerEndpoints.write(home = statusId, notifications = null))
    }

    internal companion object {
        /** Status ids are numbers too long for a Long: a longer one is newer, else the later one in text order. */
        fun isNewer(id: String, than: String): Boolean = if (id.length !=
            than.length
        ) {
            id.length > than.length
        } else {
            id > than
        }
    }
}
