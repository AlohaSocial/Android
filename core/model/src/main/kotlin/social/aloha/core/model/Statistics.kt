// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/** `{"2026-01": 12, "2026-02": 7}`: numbers by key. */
@Serializable
public data class NumberMap(val values: Map<String, Double> = emptyMap()) {
    public operator fun get(key: String): Double? = values[key]

    /** Sorted by key, which for months is chronological. */
    val sorted: List<Pair<String, Double>> get() = values.toSortedMap().toList()
}

@Serializable
public data class NamedCount(val name: String, val count: Int) {
    val id: String get() = name
}

/** Somebody the reader trades replies with. */
@Serializable
public data class ConversationPartner(val account: String, val replies: Int) {
    val id: String get() = account
}

/** `GET /api/v1/statistics`. Every map is empty where the server sent nothing. */
@Serializable
public data class AccountStatistics(
    val account: StatisticsAccount? = null,
    val window: StatisticsWindow? = null,
    val posts: NumberMap = NumberMap(),
    val engagement: NumberMap = NumberMap(),
    val rates: NumberMap = NumberMap(),
    val visibility: NumberMap = NumberMap(),
    val consistency: NumberMap = NumberMap(),
    val media: NumberMap = NumberMap(),
    val byMonth: NumberMap = NumberMap(),
    val engagementByMonth: NumberMap = NumberMap(),
    val activity: StatisticsActivity? = null,
    val partners: StatisticsPartners? = null,
    val languages: List<NamedCount> = emptyList(),
    val domains: List<NamedCount> = emptyList(),
    val hashtags: List<NamedCount> = emptyList(),
)

@Serializable
public data class StatisticsAccount(val acct: String, val followers: Int = 0, val following: Int = 0)

/** How much history the numbers cover, and whether the server stopped counting early. */
@Serializable
public data class StatisticsWindow(val days: Int = 0, val counted: Int = 0, val capped: Boolean = false)

@Serializable
public data class StatisticsActivity(
    val originals: NumberMap = NumberMap(),
    val replies: NumberMap = NumberMap(),
    val boosts: NumberMap = NumberMap(),
)

@Serializable
public data class StatisticsPartners(
    val inbound: List<ConversationPartner> = emptyList(),
    val outbound: List<ConversationPartner> = emptyList(),
)
