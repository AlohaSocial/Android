// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.HttpExchange
import social.aloha.core.network.di.IoDispatcher

/** Where the server's preview of a file is, and the credential it asks for, which is kept out of [toString]. */
public class Preview(public val url: String, public val authorization: String) {
    override fun toString(): String = "Preview(url=$url)"
}

/**
 * A person's Nextcloud Files, through the app password of the Nextcloud connection: listings and
 * previews. Not a WebDAV client: only what browsing and attaching need, as Nextcloud's own clients
 * talk to Files.
 *
 * The password goes out as HTTP Basic from the first request, not after a 401, and only to the
 * Nextcloud the account runs on; OkHttp drops it from a redirect to another host. Every failure is
 * an [ApiError]: not reachable, the password revoked ([ApiError.Unauthorised]), forbidden, or gone.
 */
@Singleton
public class NextcloudFiles @Inject constructor(
    private val exchange: HttpExchange,
    private val vault: TokenVault,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    // the user id behind each app password: WebDAV paths are made of it, and the login name may differ
    private val users = ConcurrentHashMap<String, String>()

    /** [folder], a path from the root of [account]'s files, `""` being the root, and what is in it. */
    public suspend fun list(account: SignedInAccount, folder: String): ApiResult<NextcloudFolder> =
        withSession(account) { session ->
            val url = DavPaths.file(session.filesRoot, folder, folder = true)
            if (url == null) ApiResult.Failure(ApiError.UnsafePath(folder)) else propfind(session, url, folder)
        }

    /**
     * The server's preview of [file], [edge] pixels across at least, cropped by whoever draws it; null
     * where the server has none or the Nextcloud is not connected. The etag is part of the address, so a
     * cached preview of an earlier version is never shown for an edited file.
     */
    public suspend fun preview(account: SignedInAccount, file: NextcloudFile, edge: Int): Preview? {
        val fileId = file.fileId?.takeIf { file.hasPreview } ?: return null
        val root = account.capabilities.nextcloudRoot.toHttpUrlOrNull() ?: return null
        val authorization = vault.get(VaultKey.AppPassword(account.id)) ?: return null
        val url = root.newBuilder()
            .addPathSegments("index.php/core/preview")
            .addQueryParameter("fileId", fileId.toString())
            .addQueryParameter("x", edge.toString())
            .addQueryParameter("y", edge.toString())
            .addQueryParameter("a", "1")
            .addQueryParameter("mode", "cover")
            .addQueryParameter("c", file.etag)
            .build()
        return Preview(url.toString(), authorization)
    }

    private suspend fun propfind(session: DavSession, url: HttpUrl, folder: String): ApiResult<NextcloudFolder> {
        val request = session.request(url)
            .header("Depth", "1")
            .method("PROPFIND", PROPFIND.toRequestBody(XML))
            .build()
        return exchange.exchange(request, "PROPFIND files") { response ->
            when (response.code) {
                MULTI_STATUS -> listing(response, session.filesRoot, folder, io)
                else -> exchange.failure(response)
            }
        }
    }

    /** [block] with the Nextcloud, the password and the user id [account]'s requests are made with. */
    private suspend fun <T> withSession(
        account: SignedInAccount,
        block: suspend (DavSession) -> ApiResult<T>,
    ): ApiResult<T> {
        val root = account.capabilities.nextcloudRoot.toHttpUrlOrNull()
        val authorization = vault.get(VaultKey.AppPassword(account.id))
        val session: ApiResult<DavSession> = when {
            root == null -> ApiResult.Failure(ApiError.NotFound)

            authorization == null -> ApiResult.Failure(ApiError.Unauthorised(message = null))

            else -> users[authorization]?.let { ApiResult.Success(DavSession(root, authorization, it)) }
                ?: lookUp(root, authorization)
        }
        return session.then { block(it) }
    }

    /** Asks the Nextcloud whose user [authorization] is, once per password. */
    private suspend fun lookUp(root: HttpUrl, authorization: String): ApiResult<DavSession> {
        val request = Request.Builder()
            .url(root.newBuilder().addPathSegments("ocs/v2.php/cloud/user").addQueryParameter("format", "json").build())
            .header("Authorization", authorization)
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()
        return exchange.exchange(request, "GET cloud/user") { response ->
            val id = if (response.isSuccessful) userId(withContext(io) { response.body.string() }) else null
            when {
                !response.isSuccessful -> exchange.failure(response)
                id == null -> ApiResult.Failure(ApiError.Decoding("GET cloud/user", "no user id"))
                else -> ApiResult.Success(DavSession(root, authorization, id).also { users[authorization] = id })
            }
        }
    }

    private fun userId(body: String): String? = try {
        Json.parseToJsonElement(body).jsonObject["ocs"]?.jsonObject?.get("data")?.jsonObject?.get("id")
            ?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() && !DavPaths.isDotSegment(it) }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        /** Only what a listing shows. */
        val PROPFIND = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
              <d:prop>
                <d:resourcetype/>
                <d:getcontenttype/>
                <d:getlastmodified/>
                <d:getetag/>
                <oc:fileid/>
                <oc:size/>
                <oc:permissions/>
                <nc:has-preview/>
                <nc:is-encrypted/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
    }
}
