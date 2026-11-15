// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.security.MessageDigest
import kotlinx.serialization.Serializable

/**
 * How the Play build's phone app hands its account to the watch over Wear's Data Layer. Only messages:
 * a message is delivered once and kept by no one, where a data item would leave the token in Google Play
 * services' own store on both devices.
 */
public object WatchHandover {
    /** The capability the phone app announces, so the watch finds a phone that has it. */
    public const val PHONE_CAPABILITY: String = "aloha_phone"

    /**
     * The watch asks for the account in use on the phone. After the server refused its token, the body is
     * that token's [digest], and the phone answers with the account holding it, renewed; otherwise empty.
     */
    public const val REQUEST: String = "/aloha/handover/request"

    /** The phone answers with a [HandedOverAccount], or an empty body when nobody is signed in. */
    public const val ACCOUNT: String = "/aloha/handover/account"

    /**
     * The phone signed an account out; the body is a [HandedOverAccount] without its token, carrying the
     * token's [digest] instead, so a watch forgets only the very session that ended.
     */
    public const val SIGNED_OUT: String = "/aloha/handover/signed-out"

    /**
     * The phone renewed a handed-over account's token: the body is a [HandedOverAccount] with the new token
     * and the old token's [digest], so only a watch holding that very session takes it. A refresh token
     * never leaves the phone.
     */
    public const val RENEWED: String = "/aloha/handover/renewed"

    /** A token's SHA-256, in hex: names a session in a message without giving the token away. */
    public fun digest(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}

/** The phone's account as the watch receives it; its token stays the phone's, shared, never revoked by the watch. */
@Serializable
public data class HandedOverAccount(
    val apiBase: String,
    val handle: String,
    val token: String = "",
    val tokenDigest: String = "",
) {
    override fun toString(): String = "HandedOverAccount($handle at $apiBase)"
}
