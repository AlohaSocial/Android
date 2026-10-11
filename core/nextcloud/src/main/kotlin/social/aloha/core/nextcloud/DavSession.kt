// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParserException
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.HttpExchange

/**
 * What one person's requests to their Nextcloud are made with: where it is, the app password, and the user
 * id WebDAV paths are made of.
 */
internal class DavSession(val root: HttpUrl, val authorization: String, val userId: String) {
    val filesRoot: HttpUrl = DavPaths.filesRoot(root, userId)

    fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url).header("Authorization", authorization)
}

internal const val MULTI_STATUS: Int = 207

internal val XML: MediaType = "application/xml; charset=utf-8".toMediaType()

/** [response], a listing, read off the caller's thread with each entry's path below [filesRoot]. */
internal suspend fun listing(
    response: Response,
    filesRoot: HttpUrl,
    folder: String,
    io: CoroutineDispatcher,
): ApiResult<NextcloudFolder> = try {
    ApiResult.Success(withContext(io) { Multistatus.parse(response.body.byteStream(), filesRoot, folder) })
} catch (e: XmlPullParserException) {
    ApiResult.Failure(ApiError.Decoding("PROPFIND", e.message.orEmpty()))
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Transport(e))
}

/** What [next] makes of a success; a failure stays what it was. */
internal inline fun <T, R> ApiResult<T>.then(next: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Failure -> this
    is ApiResult.Success -> next(value)
}

/** [response] as the failure it says it is. */
internal suspend fun HttpExchange.failure(response: Response): ApiResult.Failure = ApiResult.Failure(error(response))
