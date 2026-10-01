// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.video

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.database.WatchPositionDao
import social.aloha.core.database.WatchPositionEntity
import social.aloha.core.model.ContinueWatchingItem
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.WatchPositionRules
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.VideoEndpoints

/** A video the reader is part way through: the post, and how far they got. */
public data class PartWatched(val status: Status, val item: ContinueWatchingItem)

/**
 * How far the reader got in each video: told to their server, which keeps it for them alone and never
 * federates it, and mirrored here so a progress bar is right offline. Nothing under ten seconds is told;
 * a video watched past 95 % is forgotten on both sides, as the server forgets it. Reports while playing
 * come at most every five seconds; one the screen forces (a pause, leaving) goes whenever the position
 * moved since the last.
 */
@Singleton
public class WatchPositions @Inject constructor(
    private val clients: ClientFactory,
    private val dao: WatchPositionDao,
    private val statuses: StatusRepository,
    private val clock: Clock,
) {
    private class Reported(val atMillis: Long, val positionSeconds: Double)

    private val reported = ConcurrentHashMap<String, Reported>()

    /** How far through each part-watched video is, by post, between 0 and 1. */
    public fun observe(accountId: String): Flow<Map<String, Double>> = dao.observe(accountId).map { rows ->
        rows.associate { it.statusId to fraction(it.positionSeconds, it.durationSeconds) }
    }

    /** Where to start [statusId] again, in seconds; none for a video not started or already finished. */
    public suspend fun resumeAt(accountId: String, statusId: String): Double? = dao.get(accountId, statusId)
        ?.positionSeconds?.takeIf { it >= WatchPositionRules.MINIMUM_REPORTABLE_SECONDS }

    public suspend fun report(
        reader: SignedInAccount,
        statusId: String,
        positionSeconds: Double,
        durationSeconds: Double,
        forced: Boolean = false,
    ) {
        if (!WatchPositionRules.shouldReport(positionSeconds, durationSeconds)) return
        val key = "${reader.id}|$statusId"
        val now = clock.millis()
        val last = reported[key]
        val tooSoon = last != null && now - last.atMillis < GAP_MILLIS
        val unmoved = last != null && abs(last.positionSeconds - positionSeconds) < 1
        if (if (forced) unmoved else tooSoon) return
        reported[key] = Reported(now, positionSeconds)
        if (WatchPositionRules.isComplete(positionSeconds, durationSeconds)) {
            dao.forget(reader.id, statusId)
        } else {
            dao.set(WatchPositionEntity(reader.id, statusId, positionSeconds, durationSeconds, now))
        }
        if (reader.capabilities.watchPositions) {
            clients.answer(reader, VideoEndpoints.reportWatched(statusId, positionSeconds, durationSeconds))
        }
    }

    /** Takes [statusId] off the videos to carry on with, here and on the server. */
    public suspend fun remove(reader: SignedInAccount, statusId: String): Answer<Unit> {
        dao.forget(reader.id, statusId)
        return clients.answer(reader, VideoEndpoints.forgetWatched(statusId))
    }

    /**
     * The videos to carry on with, as the server keeps them, each with its post: fetched where the server
     * sent it without its author, which Nextcloud Social does, and dropped where it is gone.
     */
    public suspend fun continueWatching(reader: SignedInAccount): Answer<List<PartWatched>> {
        val answer = clients.answer(reader, VideoEndpoints.continueWatching())
        if (answer !is Answer.Got) return answer as Answer.Missed
        val watched = answer.value.filterNot {
            WatchPositionRules.isComplete(it.position, it.duration) ||
                it.position < WatchPositionRules.MINIMUM_REPORTABLE_SECONDS
        }.mapNotNull { item ->
            val status = item.status ?: statuses.get(reader.id, item.statusId)
                ?: (clients.answer(reader, StatusEndpoints.status(item.statusId)) as? Answer.Got)?.value
            status?.let { PartWatched(it, item) }
        }
        statuses.saveAll(reader.id, watched.map { it.status })
        watched.forEach {
            dao.set(
                WatchPositionEntity(reader.id, it.status.id, it.item.position, it.item.duration, clock.millis()),
            )
        }
        return Answer.Got(watched)
    }

    private fun fraction(position: Double, duration: Double): Double =
        if (duration > 0) (position / duration).coerceIn(0.0, 1.0) else 0.0

    private companion object {
        val GAP_MILLIS = (WatchPositionRules.MINIMUM_REPORT_GAP_SECONDS * 1_000).toLong()
    }
}
