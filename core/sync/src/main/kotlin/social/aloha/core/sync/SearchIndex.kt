// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.builtintypes.Thing
import androidx.appsearch.platformstorage.PlatformStorage
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the phone's search keeps what the app gives it, an account's at a time. */
public interface SearchIndex {
    /** Puts [documents] in [namespace] in place of what was there. */
    public suspend fun replace(namespace: String, documents: List<Any>)

    /** Takes everything in [namespace] out. */
    public suspend fun remove(namespace: String)

    /** Takes everything the app put in out. */
    public suspend fun clear()
}

/**
 * Below Android 12 there is no system index to put anything in: the launcher's search cannot read the
 * app's own, so nothing is stored at all.
 */
public object NoSearchIndex : SearchIndex {
    override suspend fun replace(namespace: String, documents: List<Any>): Unit = Unit

    override suspend fun remove(namespace: String): Unit = Unit

    override suspend fun clear(): Unit = Unit
}

/**
 * The system's AppSearch index, which the launcher's search reads. Its schema is AppSearch's own
 * `Thing`, which system surfaces know how to show; the session is opened once.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal class PlatformSearchIndex(private val context: Context) : SearchIndex {
    private val mutex = Mutex()
    private var session: AppSearchSession? = null

    override suspend fun replace(namespace: String, documents: List<Any>) {
        val opened = session()
        opened.removeAsync("", inNamespace(namespace)).awaited()
        if (documents.isNotEmpty()) {
            opened.putAsync(
                PutDocumentsRequest.Builder().addDocuments(documents).build(),
            ).awaited()
        }
        opened.requestFlushAsync().awaited()
    }

    override suspend fun remove(namespace: String) {
        val opened = session()
        opened.removeAsync("", inNamespace(namespace)).awaited()
        opened.requestFlushAsync().awaited()
    }

    override suspend fun clear() {
        val opened = session()
        opened.removeAsync("", SearchSpec.Builder().build()).awaited()
        opened.requestFlushAsync().awaited()
    }

    private fun inNamespace(namespace: String) = SearchSpec.Builder().addFilterNamespaces(namespace).build()

    private suspend fun session(): AppSearchSession = mutex.withLock {
        session ?: PlatformStorage.createSearchSessionAsync(
            PlatformStorage.SearchContext.Builder(context, DATABASE).build(),
        ).awaited().also { opened ->
            val schema = SetSchemaRequest.Builder()
                .addDocumentClasses(Thing::class.java)
                .setForceOverride(true)
                .build()
            opened.setSchemaAsync(schema).awaited()
            session = opened
        }
    }

    private companion object {
        const val DATABASE = "device-search"
    }
}

/** Waits for the future without blocking a thread; cancelling the wait cancels the future. */
internal suspend fun <T> ListenableFuture<T>.awaited(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel(false) }
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            } catch (e: java.util.concurrent.CancellationException) {
                continuation.cancel(e)
            }
        },
        Runnable::run,
    )
}
