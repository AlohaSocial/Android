// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * Which modes the navigation shows besides Home and Notifications. Photos, Video and Shorts always
 * exist; News and Audio only once turned on, and News only where the server picks such posts out. A
 * rail or drawer has room for all of them; a phone's bar holds three, the [phone] slots, where a mode
 * turned on takes the place of one of the three always there.
 */
public data class ModeChoices(val optional: Set<FeedMode> = emptySet(), val phone: List<FeedMode> = PHONE) {
    /** For a rail or drawer: every mode there is, in their order. */
    public fun wide(capabilities: ServerCapabilities): List<FeedMode> =
        FeedMode.entries.filter { it != FeedMode.Home && shown(it, capabilities) }

    /**
     * For a phone's bar: the three slots, each the mode chosen for it while that mode is shown, else
     * the one always there that it replaced.
     */
    public fun narrow(capabilities: ServerCapabilities): List<FeedMode> =
        PHONE.indices.map { slot -> phone.getOrNull(slot)?.takeIf { shown(it, capabilities) } ?: PHONE[slot] }

    /**
     * [mode] turned on in phone [slot] (0 to 2, Shorts' by default), taking it from whatever held it, or
     * turned off, its slot back to what is always there.
     */
    public fun turned(mode: FeedMode, on: Boolean, slot: Int = PHONE.lastIndex): ModeChoices {
        require(mode.isOptional) { "$mode is always there" }
        val freed = phone.mapIndexed { index, held -> if (held == mode) PHONE[index] else held }
        if (!on) return copy(optional = optional - mode, phone = freed)
        return copy(
            optional = optional + mode,
            phone = freed.toMutableList().also {
                it[slot.coerceIn(PHONE.indices)] =
                    mode
            },
        )
    }

    /** The phone slot [mode] holds, or null where it is not on a phone's bar. */
    public fun slotOf(mode: FeedMode): Int? = phone.indexOf(mode).takeIf { it >= 0 }

    private fun shown(mode: FeedMode, capabilities: ServerCapabilities): Boolean =
        (!mode.isOptional || mode in optional) && capabilities.supports(mode)

    public companion object {
        public val PHONE: List<FeedMode> = listOf(FeedMode.Photos, FeedMode.Video, FeedMode.Shorts)
    }
}
