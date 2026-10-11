// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import java.io.File
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.testing.FixtureCorpus

@RunWith(RobolectricTestRunner::class)
class NextcloudUploadTest {
    private val fixture = FilesFixture().apply { connect() }
    private val dav = fixture.dav
    private val sent = mutableListOf<Long>()
    private val upload = "/remote.php/dav/uploads/alice/upload-1"

    @After
    fun close() {
        fixture.close()
    }

    /** A file whose bytes each say where they are, so a chunk cut at the wrong place shows. */
    private fun file(size: Long) = File.createTempFile("upload", ".bin").apply {
        outputStream().buffered().use { out -> repeat(size.toInt()) { out.write(it % PATTERN) } }
        deleteOnExit()
    }

    private fun uploaded(source: File, name: String = "beach.png") = runBlocking {
        val asked = NextcloudUpload("Aloha fixtures", name, source, "upload-1")
        fixture.files.upload(fixture.account, asked) { done, _ -> sent += done }
    }

    @Test
    fun `a small file goes in one request that will not overwrite`() {
        dav.answer("PUT", "/remote.php/dav/files/alice/Aloha%20fixtures/beach.png", 201)
        val source = file(1_000)
        assertEquals(ApiResult.Success("Aloha fixtures/beach.png"), uploaded(source))
        val put = dav.asked.single { it.method == "PUT" }
        assertEquals("*", put.headers["If-None-Match"])
        assertArrayEquals(source.readBytes(), put.body?.toByteArray())
        assertEquals(1_000L, sent.last())
    }

    @Test
    fun `a name already taken is followed by the next free one`() {
        dav.answer("PUT", "/remote.php/dav/files/alice/Aloha%20fixtures/notes%20(2).txt", 201)
        assertEquals(ApiResult.Success("Aloha fixtures/notes (2).txt"), uploaded(file(10), "notes.txt"))
    }

    @Test
    fun `a large file goes in chunks of ten MiB, cut where they should be, then put together`() {
        acceptChunks()
        val source = file(CHUNK_BYTES * 2 + 5)
        assertEquals(ApiResult.Success("Aloha fixtures/beach.png"), uploaded(source))
        val destination = fixture.server.url("/remote.php/dav/files/alice/Aloha%20fixtures/beach.png").toString()
        val mkcol = dav.asked.single { it.method == "MKCOL" }
        assertEquals(upload, mkcol.url.encodedPath)
        assertEquals(destination, mkcol.headers["Destination"])
        val chunks = dav.asked.filter { it.method == "PUT" }
        assertEquals(listOf("$upload/1", "$upload/2", "$upload/3"), chunks.map { it.url.encodedPath })
        val bytes = source.readBytes()
        assertArrayEquals(bytes, chunks.flatMap { checkNotNull(it.body).toByteArray().toList() }.toByteArray())
        assertEquals(listOf(CHUNK_BYTES, CHUNK_BYTES, 5L), chunks.map { checkNotNull(it.body).size.toLong() })
        assertTrue(chunks.all { it.headers["OC-Total-Length"] == bytes.size.toString() })
        val move = dav.asked.single { it.method == "MOVE" }
        assertEquals("$upload/.file", move.url.encodedPath)
        assertEquals("F", move.headers["Overwrite"])
        assertEquals(destination, move.headers["Destination"])
        assertEquals(bytes.size.toLong(), sent.last())
    }

    @Test
    fun `an upload asked again carries on from the chunks already there`() {
        acceptChunks()
        dav.answer("MKCOL", upload, 405)
        dav.answer("PROPFIND", "$upload/", 207, chunkListing(1 to CHUNK_BYTES, 2 to 7))
        uploaded(file(CHUNK_BYTES * 2 + 5))
        assertEquals(
            listOf("$upload/2", "$upload/3"),
            dav.asked.filter {
                it.method == "PUT"
            }.map { it.url.encodedPath },
        )
    }

    @Test
    fun `a name taken when the chunks are put together moves them to the next free one, sending nothing again`() {
        acceptChunks()
        val earlier = dav.handler
        dav.handler = { request ->
            val taken = request.method == "MOVE" && request.headers["Destination"]?.endsWith("/beach.png") == true
            if (taken) MockResponse.Builder().code(412).build() else earlier(request)
        }
        assertEquals(ApiResult.Success("Aloha fixtures/beach (2).png"), uploaded(file(CHUNK_BYTES + 1)))
        assertEquals(2, dav.asked.count { it.method == "PUT" })
    }

    @Test
    fun `a name that is no file name is refused before anything is sent`() {
        val refused = uploaded(file(10), "../escape.png")
        assertTrue((refused as ApiResult.Failure).error is ApiError.UnsafePath)
        assertTrue(dav.asked.none { it.method == "PUT" })
    }

    @Test
    fun `an upload folder's chunks are read with their lengths, as Nextcloud lists them`() {
        val captured = FixtureCorpus("nextcloud-36-dav").named("dav/propfind-upload.json")
        val folder = fixture.server.url("/remote.php/dav/uploads/alice/aloha-chunk-probe/")
        val listed = Multistatus.parse(captured.body.byteInputStream(), folder, folder = "")
        assertEquals(mapOf(".file" to 1500L, "1" to 1200L, "2" to 300L), listed.files.associate { it.name to it.size })
    }

    @Test
    fun `names follow Nextcloud's way of naming a copy`() {
        assertEquals(listOf("photo.jpg", "photo (2).jpg", "photo (3).jpg"), freeNames("photo.jpg").take(3).toList())
        assertEquals(listOf(".bashrc", ".bashrc (2)"), freeNames(".bashrc").take(2).toList())
        assertEquals(MAX_NAMES, freeNames("archive").count())
    }

    @Test
    fun `chunks cover the file once, with no empty one at the end`() {
        assertEquals(listOf(1 to (0L until CHUNK_BYTES)), chunks(CHUNK_BYTES))
        assertEquals(
            listOf(1 to (0L until CHUNK_BYTES), 2 to (CHUNK_BYTES until CHUNK_BYTES + 1)),
            chunks(CHUNK_BYTES + 1),
        )
        assertEquals(emptyList<Pair<Int, LongRange>>(), chunks(0))
    }

    private fun acceptChunks() {
        val earlier = dav.handler
        dav.handler = { request ->
            when (request.method) {
                "MKCOL", "PUT", "MOVE" -> MockResponse.Builder().code(201).build()
                else -> earlier(request)
            }
        }
    }

    private fun chunkListing(vararg chunks: Pair<Int, Long>) = chunks.joinToString(
        prefix = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">""" +
            """<d:response><d:href>$upload/</d:href></d:response>""",
        postfix = "</d:multistatus>",
        separator = "",
    ) { (number, size) ->
        "<d:response><d:href>$upload/$number</d:href><d:propstat><d:prop>" +
            "<d:getcontentlength>$size</d:getcontentlength></d:prop></d:propstat></d:response>"
    }

    private companion object {
        const val PATTERN = 251
    }
}
