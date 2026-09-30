// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * How often the app asks an account's server what is new while it is open. [Normal] keeps the interval
 * table as it is, [Frequent] halves it, [BatterySaver] triples it; [Manual] never asks on a timer, only
 * when the person refreshes, and [multiplier] is null for it.
 */
@Serializable
public enum class PollFrequency(public val multiplier: Double?) {
    Frequent(HALF),
    Normal(1.0),
    BatterySaver(THRICE),
    Manual(null),
}

private const val HALF = 0.5
private const val THRICE = 3.0
