// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/** What to paste into the old server's "move to" box. */
@Serializable
public data class MigrationAnnouncement(val handle: String, val address: String)

/** Which of some handles exist somewhere, one entry per handle. */
@Serializable
public data class MigrationLookup(val entries: List<MigrationEntry> = emptyList())

@Serializable
public data class MigrationEntry(val handle: String, val found: Boolean, val account: Account? = null) {
    val id: String get() = handle
}

/**
 * A server's account of what an import did. The keys vary by kind (`followed`, `skipped`,
 * `imported`, …), so they are kept as they came, sorted by key.
 */
@Serializable
public data class MigrationReport(val lines: List<MigrationLine> = emptyList(), val log: List<String> = emptyList())

@Serializable
public data class MigrationLine(val key: String, val value: String)

/** The lists an account can take somewhere else or bring here. */
public enum class MigrationListKind(override val wire: String) : WireValue {
    Following("following"),
    Followers("followers"),
    Blocks("blocks"),
    Mutes("mutes"),
    Lists("lists"),
    ;

    /** The route an import of this kind lives at: following goes in as `follows`, the rest keep their name. */
    public val importPath: String get() = if (this == Following) "follows" else wire
}
