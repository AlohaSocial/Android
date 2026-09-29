// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod

private fun Endpoint.formValue(name: String): String? = (body as? Body.Form)?.fields?.firstOrNull {
    it.name == name
}?.value

private fun Endpoint.queryValue(name: String): String? = query.firstOrNull { it.name == name }?.value

class ModerationEndpointsTest {
    @Test
    fun `the reports queue defaults to unresolved and goes out on the bearer token`() {
        val endpoint = ModerationEndpoints.reports().endpoint
        assertEquals("api/v1/admin/reports", endpoint.path)
        assertEquals("false", endpoint.queryValue("resolved"))
        assertEquals(Authentication.Bearer, endpoint.authentication)
    }

    @Test
    fun `an account action carries the report it was taken from`() {
        val endpoint = AdminAccountEndpoints.act(
            "12",
            AdminAccountAction.Suspend,
            note = "spam",
            reportId = "7",
        ).endpoint
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("api/v1/admin/accounts/12/action", endpoint.path)
        assertEquals("suspend", endpoint.formValue("type"))
        assertEquals("spam", endpoint.formValue("text"))
        assertEquals("7", endpoint.formValue("report_id"))
    }

    @Test
    fun `an action with no note and no report sends neither`() {
        val endpoint = AdminAccountEndpoints.act("12", AdminAccountAction.Silence).endpoint
        assertEquals("silence", endpoint.formValue("type"))
        assertNull(endpoint.formValue("text"))
        assertNull(endpoint.formValue("report_id"))
    }

    @Test
    fun `any is the absence of a filter, not a filter of its own`() {
        val unfiltered = AdminAccountEndpoints.accounts().endpoint
        assertNull(unfiltered.queryValue("origin"))
        assertNull(unfiltered.queryValue("status"))
        val filtered = AdminAccountEndpoints.accounts(
            AdminAccountEndpoints.Origin.Remote,
            AdminAccountEndpoints.Standing.Suspended,
        )
        assertEquals("remote", filtered.endpoint.queryValue("origin"))
        assertEquals("suspended", filtered.endpoint.queryValue("status"))
    }

    @Test
    fun `a trend decision names its kind in the path`() {
        assertEquals(
            "api/v1/admin/trends/tags/nextcloud/reject",
            ModerationEndpoints.rejectTrend(ModerationEndpoints.TrendKind.Tags, "nextcloud").endpoint.path,
        )
        assertEquals(
            "api/v1/admin/trends/statuses/9/approve",
            ModerationEndpoints.approveTrend(ModerationEndpoints.TrendKind.Statuses, "9").endpoint.path,
        )
    }

    @Test
    fun `report and account actions post to their own paths`() {
        assertEquals("api/v1/admin/reports/7/assign_to_self", ModerationEndpoints.assignReportToSelf("7").endpoint.path)
        assertEquals(HttpMethod.POST, ModerationEndpoints.resolveReport("7").endpoint.method)
        assertEquals("api/v1/admin/accounts/3/unsensitive", AdminAccountEndpoints.unsensitive("3").endpoint.path)
    }

    @Test
    fun `the about-this-server routes need no viewer`() {
        assertEquals(Authentication.None, InstanceEndpoints.activity().endpoint.authentication)
        assertEquals(Authentication.None, InstanceEndpoints.domainBlocks().endpoint.authentication)
    }

    @Test
    fun `annual report routes are built per year`() {
        assertEquals("api/v1/annual_reports", AnnualReportEndpoints.all().endpoint.path)
        assertEquals("api/v1/annual_reports/2025", AnnualReportEndpoints.year(2025).endpoint.path)
        assertEquals("api/v1/annual_reports/2025/state", AnnualReportEndpoints.state(2025).endpoint.path)
        assertEquals(HttpMethod.POST, AnnualReportEndpoints.generate(2025).endpoint.method)
        assertEquals("api/v1/annual_reports/2025/read", AnnualReportEndpoints.markRead(2025).endpoint.path)
    }
}
