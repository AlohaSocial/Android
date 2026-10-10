// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.nextcloud

import java.io.OutputStream
import javax.inject.Inject
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.toAnswer
import social.aloha.core.model.AccountStatistics
import social.aloha.core.model.AuthorizedApp
import social.aloha.core.model.MigrationListKind
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.VideoChannel
import social.aloha.core.model.WeeklyRecap
import social.aloha.core.network.endpoints.AuthorizedAppEndpoints
import social.aloha.core.network.endpoints.ChannelEndpoints
import social.aloha.core.network.endpoints.ExportEndpoints
import social.aloha.core.network.endpoints.MemoryEndpoints
import social.aloha.core.network.endpoints.StatisticsEndpoints

/**
 * What Nextcloud Social keeps for an account and gives only to its Nextcloud session: memories, the
 * weekly recap, statistics, video channels, the apps signed in to it, and the account as an export.
 * Every call needs the Nextcloud connection; without it the answer is `ApiError.Unauthorised` before
 * anything is sent.
 */
public class NextcloudExtras @Inject constructor(private val clients: ClientFactory) {
    /** The reader's own posts from this day in earlier years. */
    public suspend fun onThisDay(reader: SignedInAccount): Answer<List<Status>> =
        clients.answer(reader, MemoryEndpoints.onThisDay())

    public suspend fun recap(reader: SignedInAccount): Answer<WeeklyRecap> =
        clients.answer(reader, MemoryEndpoints.recap())

    /** Whether the server counts this week's posts against last week's; off, it counts nothing. */
    public suspend fun setRecap(reader: SignedInAccount, enabled: Boolean): Answer<Unit> =
        clients.answer(reader, MemoryEndpoints.setRecap(enabled))

    /** Over [days], 0 for everything; [fresh] counts again rather than reading the server's cache. */
    public suspend fun statistics(
        reader: SignedInAccount,
        days: Int,
        fresh: Boolean = false,
    ): Answer<AccountStatistics> = clients.answer(reader, StatisticsEndpoints.overview(days, fresh))

    /** The statistics over [days] as a CSV, written [into] as they arrive. */
    public suspend fun exportStatistics(reader: SignedInAccount, days: Int, into: OutputStream): Answer<Unit> =
        clients.forAccount(reader)?.download(StatisticsEndpoints.export(days), into).toAnswer()

    /** The whole account as an archive (posts, media, follows), written [into] as it arrives. */
    public suspend fun exportAccount(reader: SignedInAccount, into: OutputStream): Answer<Unit> =
        clients.forAccount(reader)?.download(ExportEndpoints.archive(), into).toAnswer()

    /** One of the account's lists as a CSV other servers import, written [into] as it arrives. */
    public suspend fun exportList(reader: SignedInAccount, kind: MigrationListKind, into: OutputStream): Answer<Unit> =
        clients.forAccount(reader)?.download(ExportEndpoints.list(kind), into).toAnswer()

    public suspend fun channels(reader: SignedInAccount): Answer<List<VideoChannel>> =
        clients.answer(reader, ChannelEndpoints.all())

    /** Makes a channel; the answer is that channel alone. */
    public suspend fun createChannel(
        reader: SignedInAccount,
        handle: String,
        name: String,
        description: String,
    ): Answer<List<VideoChannel>> = clients.answer(reader, ChannelEndpoints.create(handle, name, description))

    /** Renames channel [id]; its handle stays. The answer is that channel alone. */
    public suspend fun updateChannel(
        reader: SignedInAccount,
        id: String,
        name: String,
        description: String,
    ): Answer<List<VideoChannel>> = clients.answer(reader, ChannelEndpoints.update(id, name, description))

    /** The apps signed in to the account, this one among them. */
    public suspend fun authorizedApps(reader: SignedInAccount): Answer<List<AuthorizedApp>> =
        clients.answer(reader, AuthorizedAppEndpoints.all())

    /** Signs app [id] out: its tokens stop working at once. */
    public suspend fun revokeApp(reader: SignedInAccount, id: String): Answer<Unit> =
        clients.answer(reader, AuthorizedAppEndpoints.revoke(id))
}
