// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/** How an account was used over a year, in one word: Mastodon's archetypes, served verbatim. */
public enum class AnnualArchetype(override val wire: String) : WireValue {
    /** Wrote almost nothing. */
    Lurker("lurker"),

    /** Mostly boosted other people. */
    Booster("booster"),

    /** Mostly asked questions. */
    Pollster("pollster"),

    /** Mostly answered other people. */
    Replier("replier"),

    /** Mostly wrote their own posts, and was read. */
    Oracle("oracle"),

    /** A value this app does not know. */
    Unknown("__unknown"),
    ;

    public companion object {
        public fun fromWire(raw: String?): AnnualArchetype = entries.fromWire(raw, Unknown)
    }
}

/** One month of the year: what was written in it, and who arrived. [month] is 1 to 12. */
@Serializable
public data class AnnualMonth(val month: Int, val statuses: Int = 0, val followers: Int = 0)

/** A hashtag the account used, and how often. */
@Serializable
public data class AnnualHashtag(val name: String, val count: Int = 0)

/**
 * The three posts of the year, each an id into [WrappedAnnualReports.statuses]. Any of them may be
 * absent: a year with no boosted post has no `by_reblogs`.
 */
@Serializable
public data class AnnualTopStatuses(
    val byReblogs: String? = null,
    val byReplies: String? = null,
    val byFavourites: String? = null,
) {
    /** The distinct ids, in the order the report presents them. */
    val ids: List<String> get() = listOfNotNull(byReblogs, byReplies, byFavourites).distinct()
}

/** What the year held. */
@Serializable
public data class AnnualReportData(
    val archetype: AnnualArchetype = AnnualArchetype.Unknown,
    val timeSeries: List<AnnualMonth> = emptyList(),
    val topHashtags: List<AnnualHashtag> = emptyList(),
    val topStatuses: AnnualTopStatuses = AnnualTopStatuses(),
) {
    /** The busiest month with any posts, for the line that summarises the chart. */
    val busiestMonth: AnnualMonth? get() = timeSeries.filter { it.statuses > 0 }.maxByOrNull { it.statuses }

    val totalStatuses: Int get() = timeSeries.sumOf { it.statuses }

    val totalFollowers: Int get() = timeSeries.sumOf { it.followers }
}

/**
 * One year's report: Mastodon's `#Wrapstodon`, which Nextcloud Social serves at
 * `/api/v1/annual_reports` without a web page of its own.
 *
 * @property shareUrl Mastodon's public page for the report; Nextcloud Social has none and sends null.
 */
@Serializable
public data class AnnualReport(
    val year: Int,
    val data: AnnualReportData = AnnualReportData(),
    val schemaVersion: Int = 1,
    val shareUrl: String? = null,
    val accountId: String = "",
) {
    val id: Int get() = year
}

/**
 * The reports plus the accounts and statuses they name, so the year's best posts draw without a
 * second round of requests.
 */
@Serializable
public data class WrappedAnnualReports(
    val annualReports: List<AnnualReport> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val statuses: List<Status> = emptyList(),
) {
    /** The status behind one of a report's top-status ids. */
    public fun status(id: String?): Status? = id?.let { wanted -> statuses.firstOrNull { it.id == wanted } }
}

/** Whether a year has a report. Nextcloud Social never answers `generating`: its report is a query, not a job. */
public enum class AnnualReportState(override val wire: String) : WireValue {
    Available("available"),
    Pending("pending"),
    Generating("generating"),
    Ineligible("ineligible"),

    /** A value this app does not know. */
    Unknown("__unknown"),
    ;

    public companion object {
        public fun fromWire(raw: String?): AnnualReportState = entries.fromWire(raw, Unknown)
    }
}
