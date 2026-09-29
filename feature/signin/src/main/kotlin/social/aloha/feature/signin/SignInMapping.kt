// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import java.text.DateFormat
import java.util.Date
import social.aloha.core.data.DiscoveredServer
import social.aloha.core.data.ServerCertificate

internal fun DiscoveredServer.toCard(): InstanceCard =
    InstanceCard(title, domain, description, userCount, rules, isNextcloudSocial)

internal fun ServerCertificate.toSummary(): CertificateSummary = CertificateSummary(
    host = host,
    subject = subject,
    issuer = issuer,
    validUntil = DateFormat.getDateInstance(DateFormat.LONG).format(Date.from(validUntil)),
    sha256 = sha256,
)
