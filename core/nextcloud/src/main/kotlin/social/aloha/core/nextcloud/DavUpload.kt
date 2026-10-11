// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.HttpExchange
import social.aloha.core.network.map
import social.aloha.core.network.reportingProgress

/**
 * The requests that put one file into a person's files. Up to [CHUNK_BYTES] it goes in one `PUT`; above,
 * Nextcloud's chunking v2: an upload folder made with `MKCOL`, numbered chunks `PUT` into it, and a `MOVE`
 * of its `.file` that has the server put them together. Each request carries the destination and the
 * total length, which Nextcloud asks for to check space and to stream into object storage.
 *
 * A file is never overwritten: the `PUT` asks for no file to be there, the `MOVE` says not to overwrite,
 * and the 412 either answers is taken to mean the name is taken, so the next one is tried. A taken name
 * at the `MOVE` costs another `MOVE`, not the chunks again.
 */
internal class DavUpload(
    private val exchange: HttpExchange,
    private val session: DavSession,
    private val upload: NextcloudUpload,
    private val io: CoroutineDispatcher,
    private val onSent: (sent: Long, total: Long) -> Unit,
) {
    private val total = upload.source.length()
    private val chunkFolder = DavPaths.upload(session.root, session.userId, upload.id)

    /** The path the file was saved at. */
    suspend fun run(): ApiResult<String> {
        val folder = DavPaths.segments(upload.folder)
        return when {
            folder == null || !isFileName(upload.name) ->
                ApiResult.Failure(ApiError.UnsafePath("${upload.folder}/${upload.name}"))

            total <= CHUNK_BYTES -> placed(folder) { path -> put(path) }

            else -> {
                val destination = destination(folder + upload.name)
                gathered(destination)
                    .then { there -> sendChunks(there, destination) }
                    .then { placed(folder) { path -> assemble(path) } }
            }
        }
    }

    private fun destination(path: List<String>): String = DavPaths.file(session.filesRoot, path).toString()

    private suspend fun put(path: List<String>): ApiResult<Boolean> {
        val request = session.request(DavPaths.file(session.filesRoot, path))
            .header("If-None-Match", "*")
            .put(upload.source.asRequestBody(OCTET_STREAM).reportingProgress(onSent))
            .build()
        return exchange.exchange(request, "PUT files", untimed = true) { written(it) }
    }

    /** The chunks already in the upload folder, by number, after making it if it was not there. */
    private suspend fun gathered(destination: String): ApiResult<Map<Int, Long>> {
        val request = session.request(chunkFolder).header("Destination", destination).method("MKCOL", null).build()
        return exchange.exchange(request, "MKCOL upload") { response ->
            when {
                response.isSuccessful -> ApiResult.Success(emptyMap())
                response.code == METHOD_NOT_ALLOWED -> chunksThere()
                else -> exchange.failure(response)
            }
        }
    }

    private suspend fun chunksThere(): ApiResult<Map<Int, Long>> {
        val folder = chunkFolder.newBuilder().addPathSegment("").build()
        val request = session.request(folder).header("Depth", "1").method("PROPFIND", CHUNKS.toRequestBody(XML)).build()
        return exchange.exchange(request, "PROPFIND upload") { response ->
            if (response.code == MULTI_STATUS) {
                listing(response, folder, folder = "", io).map { listed ->
                    listed.files.mapNotNull { file -> file.name.toIntOrNull()?.let { it to file.size } }.toMap()
                }
            } else {
                exchange.failure(response)
            }
        }
    }

    /** Sends the chunks the upload folder does not have whole yet, in order. */
    private suspend fun sendChunks(there: Map<Int, Long>, destination: String): ApiResult<Unit> {
        val missing = chunks(total).filter { (number, range) -> there[number] != range.length() }
        var done = total - missing.sumOf { it.second.length() }
        var result: ApiResult<Unit> = ApiResult.Success(Unit)
        val pending = missing.iterator()
        while (result is ApiResult.Success && pending.hasNext()) {
            val (number, range) = pending.next()
            val before = done
            val body = FileRange(upload.source, range).reportingProgress { sent, _ -> onSent(before + sent, total) }
            val request = session.request(chunkFolder.newBuilder().addPathSegment(number.toString()).build())
                .header("Destination", destination)
                .header("OC-Total-Length", total.toString())
                .put(body)
                .build()
            result = exchange.exchange(request, "PUT chunk", untimed = true) { response ->
                if (response.isSuccessful) ApiResult.Success(Unit) else exchange.failure(response)
            }
            done += range.length()
        }
        return result
    }

    /** Has the server put the chunks together at [path]; false when a file is there already. */
    private suspend fun assemble(path: List<String>): ApiResult<Boolean> {
        val request = session.request(chunkFolder.newBuilder().addPathSegment(".file").build())
            .header("Destination", destination(path))
            .header("OC-Total-Length", total.toString())
            .header("Overwrite", "F")
            .method("MOVE", null)
            .build()
        return exchange.exchange(request, "MOVE upload", untimed = true) { written(it) }
    }

    /** True once a file was written, false where one was there already, as 412 says when asked not to overwrite. */
    private suspend fun written(response: Response): ApiResult<Boolean> = when {
        response.isSuccessful -> ApiResult.Success(true)
        response.code == PRECONDITION_FAILED -> ApiResult.Success(false)
        else -> exchange.failure(response)
    }

    /** Tries the name and the names after it in [folder] until [attempt] writes one, and returns its path. */
    private suspend fun placed(
        folder: List<String>,
        attempt: suspend (path: List<String>) -> ApiResult<Boolean>,
    ): ApiResult<String> {
        var result: ApiResult<String>? = null
        val names = freeNames(upload.name).iterator()
        while (result == null && names.hasNext()) {
            val path = folder + names.next()
            result = when (val tried = attempt(path)) {
                is ApiResult.Failure -> tried
                is ApiResult.Success -> ApiResult.Success(path.joinToString("/")).takeIf { tried.value }
            }
        }
        return result ?: ApiResult.Failure(ApiError.Unprocessable("no free name for ${upload.name}"))
    }

    private companion object {
        const val METHOD_NOT_ALLOWED = 405
        const val PRECONDITION_FAILED = 412

        /** An upload's chunks have a length and nothing else worth asking for. */
        val CHUNKS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/></d:prop></d:propfind>
        """.trimIndent()
    }
}
