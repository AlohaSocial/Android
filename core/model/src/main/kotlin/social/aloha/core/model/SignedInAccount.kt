// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant

/**
 * An account this device is signed in to. [id] is local and stable; [serverAccountId] is the
 * server's own id for the person.
 *
 * @property needsReauth the server answered 401 (tokens have no refresh), or the vault could not be
 *   read after a restore: the account stays, its cache stays, and a new sign-in resumes it.
 * @property profilePending the server could not describe the account yet; Nextcloud Social answers
 *   500 from `verify_credentials` for a new account until its avatar cache job has run.
 */
public data class SignedInAccount(
    val id: String,
    val host: String,
    val apiBase: String,
    val serverAccountId: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    val headerUrl: String?,
    val capabilities: ServerCapabilities,
    val needsReauth: Boolean,
    val profilePending: Boolean,
    val addedAt: Instant,
    val nextcloudConnected: Boolean,
) {
    /** `@user@server`, the handle as other servers know it; the stored [handle] is the bare username. */
    val qualifiedHandle: String get() = if ('@' in handle) "@${handle.removePrefix("@")}" else "@$handle@$host"
}
