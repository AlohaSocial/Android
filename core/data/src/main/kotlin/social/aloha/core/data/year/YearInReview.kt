// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.year

import javax.inject.Inject
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.WrappedAnnualReports
import social.aloha.core.network.endpoints.AnnualReportEndpoints

/**
 * The reader's years in review, as their server keeps them: Mastodon's from December, Nextcloud
 * Social's at any time, as a query rather than a job.
 */
public class YearInReview @Inject constructor(private val clients: ClientFactory) {
    /** Every report the server has for [account], newest first, with the posts they point at. */
    public suspend fun reports(account: SignedInAccount): Answer<WrappedAnnualReports> =
        when (val answer = clients.answer(account, AnnualReportEndpoints.all())) {
            is Answer.Got -> Answer.Got(
                answer.value.copy(
                    annualReports = answer.value.annualReports.sortedByDescending {
                        it.year
                    },
                ),
            )

            is Answer.Missed -> answer
        }

    /** [year] was seen, so the server stops offering it. */
    public suspend fun markRead(account: SignedInAccount, year: Int) {
        clients.answer(account, AnnualReportEndpoints.markRead(year))
    }
}
