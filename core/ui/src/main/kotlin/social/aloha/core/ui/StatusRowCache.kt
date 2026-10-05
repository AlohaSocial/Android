// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import java.util.IdentityHashMap
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Status

/**
 * Rows already built, by the status instance they were built from: the status cache hands back the same
 * instance until a status changes, so an update rebuilds only the rows it touched. It also remembers
 * the post each row showed, so an action on a row acts on what the person saw. One per screen; a
 * thread, which is its own context, builds its rows without [showContext] lines.
 */
public class StatusRowCache(private val cache: RichTextCache, private val showContext: Boolean = true) {
    private val built = IdentityHashMap<Status, Pair<Pair<List<String>?, List<String>>, StatusRowUi>>()
    private val byStatusId = HashMap<String, Status>()
    private var mapper: StatusRowMapper? = null
    private var colors: RichTextColors? = null

    /** Draws with [value] from now on; a change of theme rebuilds every row. */
    @Synchronized
    public fun use(value: RichTextColors) {
        if (value != colors) {
            colors = value
            mapper = StatusRowMapper(cache, value)
            built.clear()
        }
    }

    @Synchronized
    public fun rowFor(
        status: Status,
        viewer: String,
        warning: List<String>?,
        matches: List<String> = emptyList(),
    ): StatusRowUi {
        byStatusId[status.displayed.id] = status.displayed
        built[status]?.takeIf { it.first == warning to matches }?.let { return it.second }
        if (built.size > CAPACITY) built.clear()
        val mapper = checkNotNull(mapper) { "use() the colours first" }
        return mapper.map(status, viewer, warning, showContext, matches).also {
            built[status] =
                (warning to matches) to it
        }
    }

    @Synchronized
    public fun statusFor(statusId: String): Status? = byStatusId[statusId]

    @Synchronized
    public fun clear() {
        built.clear()
        byStatusId.clear()
    }

    private companion object {
        const val CAPACITY = 1_000
    }
}
