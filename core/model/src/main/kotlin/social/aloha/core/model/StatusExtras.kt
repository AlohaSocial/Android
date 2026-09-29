// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Where one post got to: one row per server it was sent to, and the totals. Author only.
 *
 * @property retention how long the server keeps the rows, in seconds.
 */
@Serializable
public data class DeliveryReport(
    val total: Int,
    val delivered: Int,
    val sending: Int = 0,
    val waiting: Int = 0,
    val failing: Int = 0,
    val abandoned: Int = 0,
    val retention: Int = DEFAULT_RETENTION,
    val instances: List<DeliveryInstance> = emptyList(),
) {
    public companion object {
        public const val DEFAULT_RETENTION: Int = 604_800
    }
}

@Serializable
public data class DeliveryInstance(
    val host: String,
    val state: DeliveryState,
    val tries: Int = 1,
    @Serializable(with = InstantSerializer::class) val last: Instant? = null,
) {
    val id: String get() = host
}

@Serializable
public enum class DeliveryState(override val wire: String) : WireValue {
    Delivered("delivered"),
    Sending("sending"),
    Waiting("waiting"),
    Failing("failing"),
    Abandoned("abandoned"),
    ;

    public companion object {
        /** A missing or unknown state is `waiting`. */
        public fun fromWire(raw: String?): DeliveryState = entries.fromWire(raw, Waiting)
    }
}

/** Who may quote a post. */
@Serializable
public enum class QuoteApprovalPolicy(override val wire: String) : WireValue {
    Public("public"),
    Followers("followers"),
    Nobody("nobody"),
}

/**
 * The body of creating or changing a collection.
 *
 * @property visibility `public`, `private` or `draft`, as Pixelfed names them.
 */
@Serializable
public data class CollectionDraft(val title: String, val description: String = "", val visibility: String = "public")
