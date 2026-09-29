// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.AnnualReportState
import social.aloha.core.model.WrappedAnnualReports
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.dto.AnnualReportStateDto
import social.aloha.core.network.dto.WrappedAnnualReportsDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/** "Your year": Mastodon's `#Wrapstodon`, which Nextcloud Social serves over the API only. */
public object AnnualReportEndpoints {
    /** Every year this account has a report for, newest first. */
    public fun all(): ApiRequest<WrappedAnnualReports> = wrapped(Endpoint("api/v1/annual_reports"))

    public fun year(year: Int): ApiRequest<WrappedAnnualReports> = wrapped(Endpoint("api/v1/annual_reports/$year"))

    /** Whether a year has a report. */
    public fun state(year: Int): ApiRequest<AnnualReportState> =
        request(Endpoint("api/v1/annual_reports/$year/state"), AnnualReportStateDto.serializer()) { it.toDomain() }

    /**
     * Asks for a report to be generated. Nextcloud Social answers at once with nothing to wait for; the
     * request is still sent before a read, because a server that needs it acts on it.
     */
    public fun generate(year: Int): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/annual_reports/$year/generate", HttpMethod.POST))

    /** Marks a report read, so the app stops offering it at the top of Home. */
    public fun markRead(year: Int): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/annual_reports/$year/read", HttpMethod.POST))

    private fun wrapped(endpoint: Endpoint) = request(endpoint, WrappedAnnualReportsDto.serializer()) { it.toDomain() }
}
