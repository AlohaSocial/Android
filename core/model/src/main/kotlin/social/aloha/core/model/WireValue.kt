// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlin.enums.EnumEntries

/** An enum case with the spelling the server uses for it. */
public interface WireValue {
    public val wire: String
}

/**
 * The case whose [WireValue.wire] is [raw], or [unknown]. Never throws: a value the app does not
 * know renders as a generic case; it never crashes and never vanishes.
 */
internal fun <E> EnumEntries<E>.fromWire(raw: String?, unknown: E): E where E : Enum<E>, E : WireValue =
    firstOrNull { it != unknown && it.wire == raw } ?: unknown

/** Whose notifications an account gets: as the push subscription's `policy` names each. */
public enum class NotificationsFrom(override val wire: String) : WireValue {
    Anyone("all"),
    Following("followed"),
    Followers("follower"),
    NoOne("none"),
}
