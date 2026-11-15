// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import social.aloha.core.model.HandedOverAccount

/**
 * The account in use on the phone, which the Play build's phone app hands over for a sign-in without a
 * step. The F-Droid build has no Google library to reach the phone with, so there it has none.
 */
interface PhoneAccounts {
    /** Whether a phone with the app on it is in reach. */
    suspend fun reachable(): Boolean

    /**
     * The phone's account, or null: nobody signed in there, or no answer in time. With the token the server
     * [refused], the phone renews the account holding it first and answers with that one.
     */
    suspend fun account(refused: String? = null): HandedOverAccount?
}
