// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.util.Base64

/** What a Nextcloud's `status.php` says about it. */
public data class NextcloudStatus(val installed: Boolean, val maintenance: Boolean, val version: String) {
    /** Installed and not in maintenance: a Login Flow can start. */
    val ready: Boolean get() = installed && !maintenance
}

/**
 * The start of Nextcloud's Login Flow v2: the page the person approves on, and where the app asks for the
 * answer with [pollToken]. The token is kept out of [toString].
 */
public data class LoginFlowStart(val loginUrl: String, val pollToken: String, val pollEndpoint: String) {
    override fun toString(): String = "LoginFlowStart(loginUrl=$loginUrl, pollEndpoint=$pollEndpoint)"
}

/**
 * What the person granted: an app password for [loginName]. It is a full credential for their Nextcloud,
 * so it is kept out of [toString] and only ever stored in the vault.
 */
public class AppPasswordGrant(public val server: String, public val loginName: String, appPassword: String) {
    /** The password as HTTP Basic, the form WebDAV and OCS take it in. */
    public val basicAuthorization: String =
        "Basic " + Base64.getEncoder().encodeToString("$loginName:$appPassword".toByteArray())

    override fun toString(): String = "AppPasswordGrant(server=$server, loginName=$loginName)"
}
