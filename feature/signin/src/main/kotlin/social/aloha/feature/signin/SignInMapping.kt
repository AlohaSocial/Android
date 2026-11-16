// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import java.text.DateFormat
import java.util.Date
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.data.DiscoveredServer
import social.aloha.core.data.ServerCertificate
import social.aloha.core.model.InstanceDescription

internal fun DiscoveredServer.toCard(): InstanceCard = InstanceCard(
    title,
    domain,
    description,
    userCount,
    rules,
    isNextcloudSocial,
    languages,
    signUpOf(registrationsUrl.takeIf { registrationsEnabled }, apiBase, isNextcloudSocial, approvalRequired),
)

/**
 * The sign-up a server reached at [apiBase] offers at [page], null while its sign-up is closed: the page where
 * it is https, or plain http on the server's own host where the server itself is reached over plain http, as
 * only a debug build allows. Null where there is no usable page.
 */
internal fun signUpOf(page: String?, apiBase: String, alohaSocial: Boolean, approval: Boolean): SignUp? {
    val url = page?.toHttpUrlOrNull() ?: return null
    val plainServer = apiBase.toHttpUrlOrNull()?.takeUnless { it.isHttps }
    if (!url.isHttps && plainServer?.host != url.host) return null
    return SignUp(url.toString(), continuesSignIn = alohaSocial, approvalRequired = approval)
}

/**
 * [page] with the app's [authorization] request as `return`, for Aloha Social to sign the new account in
 * and continue it, and the [invite] token where there is one.
 */
internal fun signUpUrl(page: String, authorization: String, invite: String?): String = page.toHttpUrl().newBuilder()
    .setQueryParameter("return", authorization)
    .apply { invite?.let { setQueryParameter("invite", it) } }
    .build()
    .toString()

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
