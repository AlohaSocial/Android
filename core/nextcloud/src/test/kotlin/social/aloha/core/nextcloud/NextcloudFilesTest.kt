// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.nextcloud

import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.HttpExchange
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.DavServer
import social.aloha.core.testing.DavServer.Companion.BASIC
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore

/** A Nextcloud on a mock server, and the Files of alice's account on it. */
internal class FilesFixture : AutoCloseable {
    val dav = DavServer()
    val server = MockWebServer().apply {
        dispatcher = dav
        start()
    }
    val vault = TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO)
    val files = NextcloudFiles(
        HttpExchange(OkHttpClient(), RateLimiter(nowMillis = { 0L }), Dispatchers.IO),
        vault,
        Dispatchers.IO,
    )
    val account = DavServer.account(server)

    fun connect() = runBlocking { vault.put(VaultKey.AppPassword(account.id), BASIC) }

    override fun close() {
        server.close()
    }
}

@RunWith(RobolectricTestRunner::class)
class NextcloudFilesTest {
    private val fixture = FilesFixture()
    private val dav = fixture.dav
    private val server = fixture.server
    private val files = fixture.files
    private val account = fixture.account

    @After
    fun close() {
        fixture.close()
    }

    private fun connected() = fixture.connect()

    private fun listed(folder: String) = runBlocking { files.list(account, folder) }

    @Test
    fun `a folder lists its files and folders, names decoded, and says what may be done in it`() {
        connected()
        val folder = (listed("Aloha fixtures") as ApiResult.Success).value
        assertEquals("Aloha fixtures", folder.folder?.path)
        assertTrue(checkNotNull(folder.folder).canAddFiles)
        val listed = folder.files
        assertEquals(listOf("Trip #1", "100% ?real", "Ünïcödé café.png", "notes.txt"), listed.map { it.name })
        val picture = listed.first { it.name == "Ünïcödé café.png" }
        assertEquals("Aloha fixtures/Ünïcödé café.png", picture.path)
        assertEquals("image/png", picture.mimeType)
        assertEquals(19128L, picture.size)
        assertEquals(5839L, picture.fileId)
        assertTrue(picture.hasPreview && picture.readable && !picture.isFolder)
        assertEquals(Instant.parse("2026-10-11T09:22:21Z"), picture.modified)
        val trip = listed.first { it.name == "Trip #1" }
        assertTrue(trip.isFolder && trip.canAddFiles)
    }

    @Test
    fun `a listing asks for one level with the password, at the user id, every segment encoded`() {
        connected()
        listed("Aloha fixtures/100% ?real")
        val propfind = dav.asked.single { it.method == "PROPFIND" }
        assertEquals("/remote.php/dav/files/alice/Aloha%20fixtures/100%25%20%3Freal/", propfind.url.encodedPath)
        assertEquals("1", propfind.headers["Depth"])
        assertEquals(BASIC, propfind.headers["Authorization"])
    }

    @Test
    fun `the user id is asked for once, and a user id is encoded like any segment`() {
        connected()
        dav.answer("GET", "/ocs/v2.php/cloud/user", 200, """{"ocs":{"data":{"id":"jane doe"}}}""")
        listed("")
        listed("")
        assertEquals(1, dav.asked.count { it.url.encodedPath == "/ocs/v2.php/cloud/user" })
        assertEquals(
            listOf("/remote.php/dav/files/jane%20doe/", "/remote.php/dav/files/jane%20doe/"),
            dav.asked.filter { it.method == "PROPFIND" }.map { it.url.encodedPath },
        )
    }

    @Test
    fun `a user id that would climb out of the files root is not taken`() {
        connected()
        dav.answer("GET", "/ocs/v2.php/cloud/user", 200, """{"ocs":{"data":{"id":".."}}}""")
        assertTrue((listed("") as ApiResult.Failure).error is ApiError.Decoding)
        assertFalse(dav.asked.any { it.method == "PROPFIND" })
    }

    @Test
    fun `a missing folder is gone and a refused password is unauthorised`() {
        connected()
        assertEquals(ApiError.NotFound, (listed("no such folder") as ApiResult.Failure).error)
        dav.answer("PROPFIND", "/remote.php/dav/files/alice/", 401, "<d:error/>")
        assertTrue((listed("") as ApiResult.Failure).error is ApiError.Unauthorised)
    }

    @Test
    fun `without the Nextcloud connection nothing is sent`() {
        assertTrue((listed("") as ApiResult.Failure).error is ApiError.Unauthorised)
        assertTrue(dav.asked.isEmpty())
    }

    @Test
    fun `a dot segment is refused before anything is sent`() {
        connected()
        assertTrue((listed("Photos/../..") as ApiResult.Failure).error is ApiError.UnsafePath)
        assertFalse(dav.asked.any { it.method == "PROPFIND" })
    }

    @Test
    fun `entries elsewhere, above the root or with a dot segment are left out`() {
        connected()
        dav.answer(
            "PROPFIND",
            "/remote.php/dav/files/alice/",
            207,
            multistatus(
                "https://elsewhere.example/remote.php/dav/files/alice/stolen.png",
                "/remote.php/dav/files/bob/theirs.png",
                "/remote.php/dav/files/alice/%2e%2e/bob/escaped.png",
                "/remote.php/dav/files/alice/kept.png",
            ),
        )
        assertEquals(listOf("kept.png"), (listed("") as ApiResult.Success).value.files.map { it.path })
    }

    @Test
    fun `an external entity an answer declares is never read`() {
        connected()
        // were the entity read, its text would become an href, and a file of that name would be listed
        val secret = File.createTempFile("secret", ".txt").apply { writeText("leaked.png") }
        val body = """<?xml version="1.0"?><!DOCTYPE d [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>""" +
            """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/remote.php/dav/files/alice/&xxe;</d:href>""" +
            """<d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response></d:multistatus>"""
        dav.answer("PROPFIND", "/remote.php/dav/files/alice/", 207, body)
        val listed = (listed("") as? ApiResult.Success)?.value?.files.orEmpty()
        assertTrue(listed.none { "leaked" in it.path })
        secret.delete()
    }

    @Test
    fun `a preview is asked for by file id and etag on the Nextcloud, with the password`() = runBlocking {
        connected()
        val picture = (listed("Aloha fixtures") as ApiResult.Success).value.files.first { it.hasPreview }
        val preview = checkNotNull(files.preview(account, picture, edge = 96))
        assertEquals(
            server.url("/index.php/core/preview?fileId=5839&x=96&y=96&a=1&mode=cover&c=${picture.etag}").toString(),
            preview.url,
        )
        assertEquals(BASIC, preview.authorization)
        assertFalse(preview.toString().contains(BASIC))
        assertNull(files.preview(account, picture.copy(hasPreview = false), edge = 96))
    }

    private fun multistatus(vararg hrefs: String) = hrefs.joinToString(
        prefix = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">""",
        postfix = "</d:multistatus>",
        separator = "",
    ) {
        "<d:response><d:href>$it</d:href><d:propstat><d:prop><d:resourcetype/><oc:permissions>RGDNVW</oc:permissions>" +
            "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
    }
}
