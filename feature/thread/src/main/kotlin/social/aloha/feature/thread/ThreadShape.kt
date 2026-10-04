// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import social.aloha.core.model.Status

/** One line of a thread, before it is drawn. */
internal sealed interface ThreadLine {
    val key: String

    /** The post the line draws; none for [More]. */
    val statusId: String?

    data class Ancestor(override val statusId: String) : ThreadLine {
        override val key: String get() = statusId
    }

    data class Focused(override val statusId: String) : ThreadLine {
        override val key: String get() = statusId
    }

    /** A reply, [depth] 1 for a reply to the focused post. */
    data class Reply(override val statusId: String, val depth: Int) : ThreadLine {
        override val key: String get() = statusId
    }

    /** [count] replies below [parentId], too deep to indent further; they open as [parentId]'s own thread. */
    data class More(val parentId: String, val count: Int, val depth: Int) : ThreadLine {
        override val key: String get() = "more-$parentId"
        override val statusId: String? get() = null
    }
}

/**
 * Lays a conversation out: the posts above in order, the focused one, then each reply under the post it
 * answers, depth first in the order the server sent them. Replies stop indenting at [MAX_DEPTH]; what
 * lies deeper collapses into one [ThreadLine.More]. A reply whose parent the server did not send joins
 * the direct replies, so nothing sent goes unshown.
 */
internal object ThreadShape {
    const val MAX_DEPTH = 5

    fun of(focusedId: String, ancestors: List<Status>, descendants: List<Status>): List<ThreadLine> {
        val known = descendants.mapTo(HashSet()) { it.id } + focusedId
        val children = descendants.groupBy { reply -> reply.inReplyToId?.takeIf { it in known } ?: focusedId }
        val lines = ancestors.map { ThreadLine.Ancestor(it.id) }.toMutableList<ThreadLine>()
        lines += ThreadLine.Focused(focusedId)
        // depth first without recursion: a thread can be thousands of replies deep
        val stack = ArrayDeque(children[focusedId].orEmpty().reversed().map { it to 1 })
        while (stack.isNotEmpty()) {
            val (reply, depth) = stack.removeLast()
            lines += ThreadLine.Reply(reply.id, depth)
            below(reply, depth, children, stack)?.let { lines += it }
        }
        return lines
    }

    /** Queues what answers [reply], or collapses it into one line once replies are [MAX_DEPTH] deep. */
    private fun below(
        reply: Status,
        depth: Int,
        children: Map<String, List<Status>>,
        stack: ArrayDeque<Pair<Status, Int>>,
    ): ThreadLine.More? {
        val below = children[reply.id].orEmpty()
        return when {
            below.isEmpty() -> null
            depth == MAX_DEPTH -> ThreadLine.More(reply.id, count(reply.id, children), depth)
            else -> null.also { below.asReversed().forEach { stack.addLast(it to depth + 1) } }
        }
    }

    /** [lines] without the replies in [held] and everything under them, which are not shown yet. */
    fun without(lines: List<ThreadLine>, held: Set<String>): List<ThreadLine> {
        if (held.isEmpty()) return lines
        var skipBelow = Int.MAX_VALUE
        return lines.filter { line ->
            val depth = (line as? ThreadLine.Reply)?.depth ?: (line as? ThreadLine.More)?.depth ?: 0
            when {
                depth > skipBelow -> false
                line is ThreadLine.Reply && line.statusId in held -> false.also { skipBelow = depth }
                else -> true.also { skipBelow = Int.MAX_VALUE }
            }
        }
    }

    private fun count(parentId: String, children: Map<String, List<Status>>): Int {
        var total = 0
        val pending = ArrayDeque(listOf(parentId))
        while (pending.isNotEmpty()) {
            val below = children[pending.removeLast()].orEmpty()
            total += below.size
            below.forEach { pending.addLast(it.id) }
        }
        return total
    }
}
