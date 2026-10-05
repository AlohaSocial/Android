// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import java.text.DateFormat
import java.util.Date
import social.aloha.core.data.DiscoveredServer
import social.aloha.core.data.ServerCertificate
import social.aloha.core.model.InstanceDescription

internal fun DiscoveredServer.toCard(): InstanceCard =
    InstanceCard(title, domain, description, userCount, rules, isNextcloudSocial, languages)

/** A server as a preview shows it, before anything says which software it runs. */
internal fun InstanceDescription.toCard(): InstanceCard = InstanceCard(
    title = title.trim().ifBlank { domain },
    domain = domain,
    description = shortDescription.trim().ifBlank { description.trim() },
    userCount = userCount,
    rules = emptyList(),
    isNextcloudSocial = false,
    languages = languages,
)

internal fun ServerCertificate.toSummary(): CertificateSummary = CertificateSummary(
    host = host,
    subject = subject,
    issuer = issuer,
    validUntil = DateFormat.getDateInstance(DateFormat.LONG).format(Date.from(validUntil)),
    sha256 = sha256,
)
