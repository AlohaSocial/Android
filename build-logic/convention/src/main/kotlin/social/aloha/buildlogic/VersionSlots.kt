// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.buildlogic

/**
 * Each application module's version code: its form-factor slot in the leading digits, the release counter
 * below. All the apps share one application id, and a device that qualifies for two of them gets the one
 * with the higher code, so the TV app outranks the phone app on a TV in every release, a Chromebook-ready
 * phone app included. docs/12-store.md has the table, the rule and the release step.
 */
internal object VersionSlots {
    private const val SLOT_SIZE = 100_000_000

    // Google Play takes version codes up to 2,100,000,000
    private const val LAST_SLOT = 20

    /**
     * A module only ever moves to a higher slot, and a retired slot is never reused: Play offers no update
     * with a lower code than the one installed. A TV app merged into the phone app takes slot 4.
     */
    private val slots = mapOf(":app" to 1, ":tv" to 2, ":wear" to 3)

    fun versionCode(module: String, base: Int): Int {
        val slot = checkNotNull(slots[module]) { "$module has no version slot in VersionSlots" }
        check(slot in 1..LAST_SLOT) { "$module's slot $slot is past Play's highest version code" }
        require(base in 1 until SLOT_SIZE) { "aloha.versionBase must be between 1 and ${SLOT_SIZE - 1}, is $base" }
        return slot * SLOT_SIZE + base
    }
}
